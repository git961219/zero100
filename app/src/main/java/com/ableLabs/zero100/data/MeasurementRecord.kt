package com.ableLabs.zero100.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "measurements")
data class MeasurementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetSpeed: Double,
    val elapsedMs: Long,
    val peakSpeed: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val memo: String = "",
    val splitsJson: String = ""   // JSON: [{"speed":60,"ms":3210},{"speed":100,"ms":5230}]
) {
    val elapsedSeconds: Double get() = elapsedMs / 1000.0

    val displayTime: String
        get() = String.format("%.2f초", elapsedSeconds)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    suspend fun getAll(): List<MeasurementRecord>

    @Query("SELECT * FROM measurements ORDER BY elapsedMs ASC LIMIT 1")
    suspend fun getBest(): MeasurementRecord?

    @Insert
    suspend fun insert(record: MeasurementRecord): Long

    @Delete
    suspend fun delete(record: MeasurementRecord)

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}

/**
 * version 1 -> 2: splitsJson 컬럼 추가
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN splitsJson TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [MeasurementRecord::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
}
