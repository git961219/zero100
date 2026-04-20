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
    val measureMode: String = "ACCELERATION", // "ACCELERATION", "DECELERATION", "COMBINED"
    val peakG: Float = 0f,             // 피크 G-force
    val vehicleId: Long = 0,           // 차량 프로필 ID (0 = 미지정)
    val accelMs: Long = 0,             // 복합: 가속 구간 시간
    val decelMs: Long = 0,             // 복합: 감속 구간 시간
    val accelDistance: Double = 0.0,    // 복합: 가속 구간 거리
    val decelDistance: Double = 0.0     // 복합: 감속 구간 거리
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

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 5): List<MeasurementRecord>

    @Query("SELECT * FROM measurements WHERE targetSpeed = :target AND measureMode = 'ACCELERATION' ORDER BY elapsedMs ASC LIMIT 1")
    suspend fun getBestByTarget(target: Double): MeasurementRecord?

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun getCount(): Int

    @Query("SELECT AVG(elapsedMs) FROM measurements WHERE targetSpeed = :target AND measureMode = 'ACCELERATION'")
    suspend fun getAverageMs(target: Double): Double?

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

/**
 * version 8 -> 9: peakG, vehicleId, accelMs, decelMs, accelDistance, decelDistance 추가 + vehicles 테이블
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE measurements ADD COLUMN peakG REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN vehicleId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN accelMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN decelMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN accelDistance REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE measurements ADD COLUMN decelDistance REAL NOT NULL DEFAULT 0.0")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vehicles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                make TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                year INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                isDefault INTEGER NOT NULL DEFAULT 0
            )
        """)
    }
}

// ── 차량 프로필 ──

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,           // "내 스팅어"
    val make: String = "",      // "KIA"
    val model: String = "",     // "Stinger GT"
    val year: Int = 0,          // 2024
    val notes: String = "",
    val isDefault: Boolean = false
)

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY isDefault DESC, name ASC")
    suspend fun getAll(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Vehicle?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: Long): Vehicle?

    @Insert
    suspend fun insert(vehicle: Vehicle): Long

    @Update
    suspend fun update(vehicle: Vehicle)

    @Delete
    suspend fun delete(vehicle: Vehicle)

    @Query("UPDATE vehicles SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE vehicles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)
}

@Database(entities = [MeasurementRecord::class, Vehicle::class], version = 9)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun vehicleDao(): VehicleDao
}
