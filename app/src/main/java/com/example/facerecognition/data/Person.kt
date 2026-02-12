package com.example.facerecognition.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 등록된 사람 정보를 저장하는 엔티티
 */
@Entity(tableName = "persons")
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val embedding: String, // JSON 형태로 저장된 벡터 배열 (여러 장의 사진에서 추출한 벡터들)
    val createdAt: Long = System.currentTimeMillis()
)
