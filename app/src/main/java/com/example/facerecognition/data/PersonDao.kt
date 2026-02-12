package com.example.facerecognition.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun getAllPersons(): Flow<List<Person>>
    
    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getPersonById(id: Long): Person?
    
    @Query("SELECT * FROM persons WHERE name = :name LIMIT 1")
    suspend fun getPersonByName(name: String): Person?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long
    
    @Update
    suspend fun updatePerson(person: Person)
    
    @Delete
    suspend fun deletePerson(person: Person)
    
    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePersonById(id: Long)
}
