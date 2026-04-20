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
    val splitsJson: String = "",   // JSON: [{"speed":10,"ms":1200,"dist":15.3,"major":false}, ...]
    val trackPointsJson: String = "", // JSON: [{"t":0,"lat":37.123,"lon":126.456,"spd":0.0}, ...]
    val isRolloutApplied: Boolean = false,
    val distanceBySpeed: Double = 0.0,  // 속도 적분 거리 (m)
    val distanceByGps: Double = 0.0,    // GPS 좌표 거리 (m)
    val gpsSource: String = "",         // "INTERNAL" or "EXTERNAL"
    val avgHdop: Double = 0.0,          // 측정 중 평균 HDOP
    val avgSatellites: Int = 0,         // 측정 중 평균 위성 수
    val updateRateHz: Int = 0,          // 측정 중 평균 수신률 (Hz)
    val distanceCheckpointsJson: String = "", // JSON: [{"dist":18.3,"label":"60ft","ms":1200,"spd":45.2}, ...]
    val measureMode: String = "ACCELERATION" // "ACCELERATION" or "DECELERATION"
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

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): MeasurementRecord?

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

/**
 * version 2 -> 3: trackPointsJson 컬럼 추가 (측정 중 GPS 궤적)
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN trackPointsJson TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * version 3 -> 4: isRolloutApplied 컬럼 추가 (1-foot rollout 보정 적용 여부)
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN isRolloutApplied INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * version 4 -> 5: distanceBySpeed, distanceByGps 컬럼 추가 (듀얼 거리 측정)
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN distanceBySpeed REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN distanceByGps REAL NOT NULL DEFAULT 0.0")
    }
}

/**
 * version 5 -> 6: gpsSource, avgHdop, avgSatellites, updateRateHz 추가
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN gpsSource TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE measurements ADD COLUMN avgHdop REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN avgSatellites INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN updateRateHz INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * version 6 -> 7: distanceCheckpointsJson 추가 (거리 기반 체크포인트)
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN distanceCheckpointsJson TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * version 7 -> 8: measureMode 추가 (가속/감속 구분)
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN measureMode TEXT NOT NULL DEFAULT 'ACCELERATION'")
    }
}

@Database(entities = [MeasurementRecord::class], version = 8)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
}
