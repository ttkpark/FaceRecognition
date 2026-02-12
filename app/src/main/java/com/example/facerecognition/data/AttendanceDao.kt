package com.example.facerecognition.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendances ORDER BY timestamp DESC")
    fun getAllAttendances(): Flow<List<Attendance>>
    
    @Query("SELECT * FROM attendances WHERE date = :date ORDER BY timestamp DESC")
    fun getAttendancesByDate(date: String): Flow<List<Attendance>>
    
    @Query("SELECT * FROM attendances WHERE personId = :personId ORDER BY timestamp DESC")
    fun getAttendancesByPerson(personId: Long): Flow<List<Attendance>>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttendance(attendance: Attendance): Long
    
    @Query("SELECT COUNT(*) FROM attendances WHERE personId = :personId AND date = :date")
    suspend fun hasAttendedToday(personId: Long, date: String): Int
    
    @Delete
    suspend fun deleteAttendance(attendance: Attendance)
    
    @Query("DELETE FROM attendances")
    suspend fun deleteAllAttendances()
}
