# API 명세서

## FaceRecognitionEngine

### extractEmbedding(faceBitmap: Bitmap): FloatArray?

얼굴 이미지에서 임베딩 벡터를 추출합니다.

**파라미터:**
- `faceBitmap`: 얼굴 이미지 (크기 무관, 내부에서 112x112로 리사이즈)

**반환값:**
- `FloatArray?`: 192차원 임베딩 벡터 (정규화됨), 실패 시 null

**예외:**
- 모델 파일이 없거나 로드 실패 시 null 반환

**사용 예:**
```kotlin
val faceBitmap = faceDetector.cropFace(bitmap, boundingBox)
val embedding = recognitionEngine.extractEmbedding(faceBitmap)
if (embedding != null) {
    // 임베딩 벡터 사용
}
```

---

### cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float

두 임베딩 벡터 간의 코사인 유사도를 계산합니다.

**파라미터:**
- `embedding1`: 첫 번째 임베딩 벡터
- `embedding2`: 두 번째 임베딩 벡터

**반환값:**
- `Float`: 코사인 유사도 (0.0 ~ 1.0, 1.0에 가까울수록 유사)

**예외:**
- 벡터 크기가 일치하지 않으면 `IllegalArgumentException`

**사용 예:**
```kotlin
val similarity = recognitionEngine.cosineSimilarity(embedding1, embedding2)
if (similarity >= 0.6f) {
    // 동일인으로 판단
}
```

---

### euclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float

두 임베딩 벡터 간의 유클리드 거리를 계산합니다.

**파라미터:**
- `embedding1`: 첫 번째 임베딩 벡터
- `embedding2`: 두 번째 임베딩 벡터

**반환값:**
- `Float`: 유클리드 거리 (작을수록 유사, 보통 1.0 이하면 동일인)

**사용 예:**
```kotlin
val distance = recognitionEngine.euclideanDistance(embedding1, embedding2)
if (distance <= 1.0f) {
    // 동일인으로 판단
}
```

---

## FaceDetector

### detectFaces(bitmap: Bitmap): List<Face>

Bitmap에서 얼굴을 감지합니다.

**파라미터:**
- `bitmap`: 분석할 이미지

**반환값:**
- `List<Face>`: 감지된 얼굴 리스트

**사용 예:**
```kotlin
val faces = faceDetector.detectFaces(bitmap)
if (faces.isNotEmpty()) {
    val face = faces[0]
    val boundingBox = face.boundingBox
    // 얼굴 영역 처리
}
```

---

### cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap

얼굴 영역을 Bitmap으로 자릅니다.

**파라미터:**
- `bitmap`: 원본 이미지
- `boundingBox`: 얼굴 영역 (Rect)

**반환값:**
- `Bitmap`: 크롭된 얼굴 이미지

**사용 예:**
```kotlin
val faceBitmap = faceDetector.cropFace(bitmap, face.boundingBox)
```

---

## FaceMatcher

### findBestMatch(currentEmbedding: FloatArray, registeredPersons: List<Person>): MatchResult?

등록된 사람들 중 가장 유사한 사람을 찾습니다.

**파라미터:**
- `currentEmbedding`: 현재 얼굴의 임베딩 벡터
- `registeredPersons`: 등록된 사람 리스트

**반환값:**
- `MatchResult?`: 매칭된 Person과 유사도, 매칭 실패 시 null

**MatchResult 구조:**
```kotlin
data class MatchResult(
    val person: Person,
    val similarity: Float  // 코사인 유사도
)
```

**사용 예:**
```kotlin
val matchResult = faceMatcher.findBestMatch(embedding, persons)
if (matchResult != null) {
    val person = matchResult.person
    val similarity = matchResult.similarity
    // 매칭 성공 처리
}
```

---

### embeddingsToJson(embeddings: List<FloatArray>): String

여러 임베딩 벡터를 JSON 문자열로 변환합니다.

**파라미터:**
- `embeddings`: 임베딩 벡터 리스트

**반환값:**
- `String`: JSON 문자열 (예: `[[0.1, 0.2, ...], [0.3, 0.4, ...]]`)

**사용 예:**
```kotlin
val embeddings = listOf(embedding1, embedding2, embedding3)
val json = faceMatcher.embeddingsToJson(embeddings)
// 데이터베이스에 저장
```

---

## FaceRecognitionViewModel

### recognizeFace(bitmap: Bitmap)

카메라 프레임에서 얼굴 인식을 수행합니다.

**파라미터:**
- `bitmap`: 카메라 프레임 이미지

**동작:**
1. 얼굴 감지
2. 임베딩 추출
3. 등록된 사람들과 비교
4. 매칭 성공 시 출석 기록

**상태 업데이트:**
- `recognizedPerson`: 인식된 사람 (StateFlow)
- `isProcessing`: 처리 중 여부 (StateFlow)

**사용 예:**
```kotlin
viewModel.recognizeFace(bitmap)
// StateFlow를 observe하여 결과 확인
```

---

### registerPerson(name: String, faceBitmaps: List<Bitmap>)

새 사용자를 등록합니다.

**파라미터:**
- `name`: 사용자 이름
- `faceBitmaps`: 얼굴 이미지 리스트 (최대 5장 권장)

**동작:**
1. 각 이미지에서 얼굴 감지
2. 임베딩 벡터 추출
3. JSON으로 변환하여 데이터베이스 저장

**상태 업데이트:**
- `isProcessing`: 처리 중 여부
- `errorMessage`: 에러 메시지 (실패 시)

**사용 예:**
```kotlin
val bitmaps = listOf(bitmap1, bitmap2, bitmap3)
viewModel.registerPerson("홍길동", bitmaps)
```

---

### deletePerson(person: Person)

등록된 사용자를 삭제합니다.

**파라미터:**
- `person`: 삭제할 Person 객체

**동작:**
- 데이터베이스에서 사용자 삭제
- 관련 출석 기록도 함께 삭제 (CASCADE)

---

## PersonDao

### getAllPersons(): Flow<List<Person>>

모든 등록된 사용자를 가져옵니다.

**반환값:**
- `Flow<List<Person>>`: 사용자 리스트 (Flow)

**사용 예:**
```kotlin
personDao.getAllPersons().collect { persons ->
    // 사용자 리스트 처리
}
```

---

### insertPerson(person: Person): Long

새 사용자를 등록합니다.

**파라미터:**
- `person`: 등록할 Person 객체

**반환값:**
- `Long`: 생성된 사용자 ID

---

## AttendanceDao

### getAllAttendances(): Flow<List<Attendance>>

모든 출석 기록을 가져옵니다.

**반환값:**
- `Flow<List<Attendance>>`: 출석 기록 리스트 (최신순)

---

### insertAttendance(attendance: Attendance): Long

출석 기록을 추가합니다.

**파라미터:**
- `attendance`: 출석 기록 객체

**반환값:**
- `Long`: 생성된 출석 기록 ID

---

### hasAttendedToday(personId: Long, date: String): Int

오늘 출석했는지 확인합니다.

**파라미터:**
- `personId`: 사용자 ID
- `date`: 날짜 ("YYYY-MM-DD" 형식)

**반환값:**
- `Int`: 출석 횟수 (0이면 미출석)
