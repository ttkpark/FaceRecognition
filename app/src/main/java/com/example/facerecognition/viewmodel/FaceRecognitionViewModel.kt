package com.example.facerecognition.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facerecognition.data.AppDatabase
import com.example.facerecognition.data.Attendance
import com.example.facerecognition.data.Person
import com.example.facerecognition.ml.FaceDetector
import com.example.facerecognition.ml.FaceMatcher
import com.example.facerecognition.ml.FaceRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class FaceRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val personDao = database.personDao()
    private val attendanceDao = database.attendanceDao()
    
    private val recognitionEngine = FaceRecognitionEngine(application)
    private val faceDetector = FaceDetector()
    private val faceMatcher = FaceMatcher(recognitionEngine)
    
    // UI 상태
    private val _recognizedPerson = MutableStateFlow<Person?>(null)
    val recognizedPerson: StateFlow<Person?> = _recognizedPerson.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _allPersons = MutableStateFlow<List<Person>>(emptyList())
    val allPersons: StateFlow<List<Person>> = _allPersons.asStateFlow()
    
    private val _attendances = MutableStateFlow<List<Attendance>>(emptyList())
    val attendances: StateFlow<List<Attendance>> = _attendances.asStateFlow()
    
    // 스로틀링을 위한 변수 (1초에 2번만 인식)
    private var lastRecognitionTime = 0L
    private val recognitionInterval = 500L // 500ms = 0.5초
    
    init {
        loadAllPersons()
        loadAttendances()
    }
    
    /**
     * 카메라 프레임에서 얼굴 인식 수행
     */
    fun recognizeFace(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()
        
        // 스로틀링: 너무 자주 실행되지 않도록 제한
        if (currentTime - lastRecognitionTime < recognitionInterval) {
            return
        }
        
        if (_isProcessing.value) {
            return
        }
        
        lastRecognitionTime = currentTime
        
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    // 1. 얼굴 감지
                    val faces = faceDetector.detectFaces(bitmap)
                    
                    if (faces.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _recognizedPerson.value = null
                            _isProcessing.value = false
                        }
                        return@withContext
                    }
                    
                    // 첫 번째 얼굴만 처리
                    val face = faces[0]
                    val faceBitmap = faceDetector.cropFace(bitmap, face.boundingBox)
                    
                    // 2. 임베딩 추출
                    val embedding = recognitionEngine.extractEmbedding(faceBitmap)
                        ?: throw Exception("임베딩 추출 실패")
                    
                    // 3. 등록된 사람들과 비교
                    val persons = personDao.getAllPersons().let { flow ->
                        flow.first() // 첫 번째 값 가져오기
                    }
                    
                    val matchResult = faceMatcher.findBestMatch(embedding, persons)
                    
                    withContext(Dispatchers.Main) {
                        if (matchResult != null) {
                            _recognizedPerson.value = matchResult.person
                            // 출석 기록
                            recordAttendance(matchResult.person.id)
                        } else {
                            _recognizedPerson.value = null
                        }
                        _isProcessing.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "인식 실패: ${e.message}"
                    _isProcessing.value = false
                }
            }
        }
    }
    
    /**
     * 새 사람 등록 (여러 장의 사진에서 임베딩 추출)
     */
    fun registerPerson(name: String, faceBitmaps: List<Bitmap>) {
        if (name.isBlank()) {
            _errorMessage.value = "이름을 입력해주세요."
            return
        }
        
        if (faceBitmaps.isEmpty()) {
            _errorMessage.value = "얼굴 사진이 필요합니다."
            return
        }
        
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            
            try {
                withContext(Dispatchers.IO) {
                    // 각 사진에서 임베딩 추출
                    val embeddings = mutableListOf<FloatArray>()
                    
                    for (bitmap in faceBitmaps) {
                        // 얼굴 감지
                        val faces = faceDetector.detectFaces(bitmap)
                        if (faces.isNotEmpty()) {
                            val faceBitmap = faceDetector.cropFace(bitmap, faces[0].boundingBox)
                            val embedding = recognitionEngine.extractEmbedding(faceBitmap)
                            if (embedding != null) {
                                embeddings.add(embedding)
                            }
                        }
                    }
                    
                    if (embeddings.isEmpty()) {
                        throw Exception("얼굴을 감지할 수 없습니다.")
                    }
                    
                    // JSON으로 변환하여 저장
                    val embeddingJson = faceMatcher.embeddingsToJson(embeddings)
                    
                    val person = Person(
                        name = name,
                        embedding = embeddingJson
                    )
                    
                    personDao.insertPerson(person)
                    
                    withContext(Dispatchers.Main) {
                        loadAllPersons()
                        _isProcessing.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "등록 실패: ${e.message}"
                    _isProcessing.value = false
                }
            }
        }
    }
    
    /**
     * 출석 기록
     */
    private fun recordAttendance(personId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val today = dateFormat.format(Date())
                    
                    // 오늘 이미 출석했는지 확인
                    val hasAttended = attendanceDao.hasAttendedToday(personId, today) > 0
                    
                    if (!hasAttended) {
                        val attendance = Attendance(
                            personId = personId,
                            date = today
                        )
                        attendanceDao.insertAttendance(attendance)
                        
                        withContext(Dispatchers.Main) {
                            loadAttendances()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 모든 등록된 사람 로드
     */
    private fun loadAllPersons() {
        viewModelScope.launch {
            personDao.getAllPersons().collect { persons ->
                _allPersons.value = persons
            }
        }
    }
    
    /**
     * 출석 기록 로드
     */
    private fun loadAttendances() {
        viewModelScope.launch {
            attendanceDao.getAllAttendances().collect { att ->
                _attendances.value = att
            }
        }
    }
    
    /**
     * 사람 삭제
     */
    fun deletePerson(person: Person) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                personDao.deletePerson(person)
            }
            loadAllPersons()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        recognitionEngine.close()
        faceDetector.close()
    }
}
