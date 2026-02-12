package com.example.facerecognition.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 출석 기록을 저장하는 엔티티
 */
@Entity(
    tableName = "attendances",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String // "YYYY-MM-DD" 형식
)
