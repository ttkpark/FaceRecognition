package com.example.facerecognition.ml

import com.example.facerecognition.data.Person
import org.json.JSONArray

/**
 * 얼굴 임베딩 벡터를 비교하여 가장 유사한 사람을 찾는 클래스
 */
class FaceMatcher(private val recognitionEngine: FaceRecognitionEngine) {
    
    // 매칭 임계값 (코사인 유사도 기준, 0.0 ~ 1.0)
    // 0.6 이상이면 동일인으로 판단 (모델에 따라 조정 필요)
    private val similarityThreshold = 0.6f
    
    /**
     * 현재 얼굴 임베딩과 등록된 사람들 중 가장 유사한 사람 찾기
     * @param currentEmbedding 현재 얼굴의 임베딩 벡터
     * @param registeredPersons 등록된 사람 리스트
     * @return 매칭된 Person과 유사도, 매칭 실패 시 null
     */
    fun findBestMatch(
        currentEmbedding: FloatArray,
        registeredPersons: List<Person>
    ): MatchResult? {
        var bestMatch: Person? = null
        var bestSimilarity = 0f
        
        for (person in registeredPersons) {
            // JSON 문자열로 저장된 여러 벡터들을 파싱
            val embeddings = parseEmbeddings(person.embedding)
            
            // 각 벡터와 비교하여 최고 유사도 찾기
            for (storedEmbedding in embeddings) {
                val similarity = recognitionEngine.cosineSimilarity(currentEmbedding, storedEmbedding)
                
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestMatch = person
                }
            }
        }
        
        // 임계값 이상이면 매칭 성공
        return if (bestSimilarity >= similarityThreshold && bestMatch != null) {
            MatchResult(bestMatch, bestSimilarity)
        } else {
            null
        }
    }
    
    /**
     * JSON 문자열로 저장된 임베딩 벡터들을 파싱
     * 형식: [[0.1, 0.2, ...], [0.3, 0.4, ...], ...]
     */
    private fun parseEmbeddings(jsonString: String): List<FloatArray> {
        return try {
            val jsonArray = JSONArray(jsonString)
            val embeddings = mutableListOf<FloatArray>()
            
            for (i in 0 until jsonArray.length()) {
                val innerArray = jsonArray.getJSONArray(i)
                val embedding = FloatArray(innerArray.length())
                for (j in 0 until innerArray.length()) {
                    embedding[j] = innerArray.getDouble(j).toFloat()
                }
                embeddings.add(embedding)
            }
            
            embeddings
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 여러 임베딩 벡터를 JSON 문자열로 변환하여 저장
     */
    fun embeddingsToJson(embeddings: List<FloatArray>): String {
        val jsonArray = JSONArray()
        for (embedding in embeddings) {
            val innerArray = JSONArray()
            for (value in embedding) {
                innerArray.put(value.toDouble())
            }
            jsonArray.put(innerArray)
        }
        return jsonArray.toString()
    }
    
    /**
     * 매칭 결과 데이터 클래스
     */
    data class MatchResult(
        val person: Person,
        val similarity: Float
    )
}
