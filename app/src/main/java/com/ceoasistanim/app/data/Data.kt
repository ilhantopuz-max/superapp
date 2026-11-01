package com.ceoasistanim.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "Genel",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long? = null,
    val done: Boolean = false,
    val repeat: String = "none", // none | daily | weekly | monthly
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val status: String = "Aday",
    val notes: String = "",
    val lastContact: Long? = null,
    val nextContact: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interactions")
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val text: String,
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startAt: Long,
    val agenda: String = "",
    val transcript: String = "",
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Note>>
    @Insert suspend fun insert(n: Note): Long
    @Delete suspend fun delete(n: Note)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC")
    fun observeAll(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?
    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL")
    suspend fun getPending(): List<Task>
    @Insert suspend fun insert(t: Task): Long
    @Update suspend fun update(t: Task)
    @Delete suspend fun delete(t: Task)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Customer>>
    @Upsert suspend fun upsert(c: Customer): Long
    @Delete suspend fun delete(c: Customer)
}

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Interaction>>
    @Insert suspend fun insert(i: Interaction): Long
    @Update suspend fun update(i: Interaction)
    @Delete suspend fun delete(i: Interaction)
    @Query("DELETE FROM interactions WHERE customerId = :customerId")
    suspend fun deleteForCustomer(customerId: Long)
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY startAt DESC")
    fun observeAll(): Flow<List<Meeting>>
    @Insert suspend fun insert(m: Meeting): Long
    @Update suspend fun update(m: Meeting)
    @Delete suspend fun delete(m: Meeting)
}

@Database(
    entities = [Note::class, Task::class, Customer::class, Interaction::class, Meeting::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun tasks(): TaskDao
    abstract fun customers(): CustomerDao
    abstract fun interactions(): InteractionDao
    abstract fun meetings(): MeetingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "ceo_asistanim.db"
            ).build().also { instance = it }
        }
    }
}
