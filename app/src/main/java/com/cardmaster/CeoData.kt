package com.cardmaster

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val category: String = "Genel",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val details: String = "",
    val dueAt: Long = 0,
    val done: Boolean = false,
    val priority: Int = 1,
    val repeatRule: String = "",
    val customerId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "events")
data class EventItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val startAt: Long = 0,
    val endAt: Long = 0,
    val location: String = "",
    val details: String = "",
    val remindMin: Int = 15,
    val kind: String = "Etkinlik",
    val repeatRule: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val status: String = "Aday",
    val lastContactAt: Long = 0,
    val nextContactAt: Long = 0,
    val details: String = "",
    val cardId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interactions")
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long = 0,
    val whenAt: Long = System.currentTimeMillis(),
    val type: String = "Not",
    val summary: String = ""
)

@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long = 0,
    val title: String = "",
    val amount: Double = 0.0,
    val status: String = "Bekliyor",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CeoDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM notes") suspend fun notesSnap(): List<Note>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putNote(n: Note): Long
    @Delete suspend fun delNote(n: Note)

    @Query("SELECT * FROM tasks ORDER BY done ASC, CASE WHEN dueAt = 0 THEN 1 ELSE 0 END, dueAt ASC")
    fun tasks(): Flow<List<TaskItem>>
    @Query("SELECT * FROM tasks WHERE done = 0") suspend fun openTasksSnap(): List<TaskItem>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun taskById(id: Long): TaskItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTask(t: TaskItem): Long
    @Delete suspend fun delTask(t: TaskItem)

    @Query("SELECT * FROM events ORDER BY startAt ASC") fun events(): Flow<List<EventItem>>
    @Query("SELECT * FROM events") suspend fun eventsSnap(): List<EventItem>
    @Query("SELECT * FROM events WHERE id = :id") suspend fun eventById(id: Long): EventItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEvent(e: EventItem): Long
    @Delete suspend fun delEvent(e: EventItem)

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE ASC") fun customers(): Flow<List<Customer>>
    @Query("SELECT * FROM customers") suspend fun customersSnap(): List<Customer>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCustomer(c: Customer): Long
    @Delete suspend fun delCustomer(c: Customer)

    @Query("SELECT * FROM interactions WHERE customerId = :cid ORDER BY whenAt DESC")
    fun interactionsOf(cid: Long): Flow<List<Interaction>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putInteraction(i: Interaction): Long
    @Query("DELETE FROM interactions WHERE customerId = :cid") suspend fun delInteractionsOf(cid: Long)

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC") fun quotes(): Flow<List<Quote>>
    @Query("SELECT * FROM quotes") suspend fun quotesSnap(): List<Quote>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putQuote(q: Quote): Long
    @Delete suspend fun delQuote(q: Quote)
    @Query("DELETE FROM quotes WHERE customerId = :cid") suspend fun delQuotesOf(cid: Long)
}

@Database(
    entities = [Note::class, TaskItem::class, EventItem::class, Customer::class,
        Interaction::class, Quote::class],
    version = 1, exportSchema = false
)
abstract class CeoDb : RoomDatabase() {
    abstract fun dao(): CeoDao

    companion object {
        @Volatile private var inst: CeoDb? = null
        fun get(c: Context): CeoDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, CeoDb::class.java, "ceo.db")
                .build().also { inst = it }
        }
    }
}
