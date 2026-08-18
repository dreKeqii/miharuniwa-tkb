package com.miharuniwa.tkb.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "week_items")
data class WeekItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val link: String,
    val isSatHach: Boolean,
    val labelText: String,
    val isNotified: Boolean = false
)

@Entity(tableName = "schedule_details")
data class ScheduleDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val weekId: String,
    val base: String, // "CS1", "CS2"
    val systemType: String, // "Trung Cấp", "Cao Đẳng", etc.
    val driveLink: String,
    val fileId: String
)

@Entity(tableName = "form_items")
data class FormItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val driveLink: String,
    val fileId: String
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM week_items")
    suspend fun getAllWeeks(): List<WeekItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeks(weeks: List<WeekItemEntity>)

    @Query("DELETE FROM week_items")
    suspend fun clearWeeks()

    @Query("SELECT * FROM schedule_details WHERE weekId = :weekId")
    suspend fun getDetailsForWeek(weekId: String): List<ScheduleDetailEntity>

    @Query("DELETE FROM schedule_details WHERE weekId = :weekId")
    suspend fun clearDetailsForWeek(weekId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetails(details: List<ScheduleDetailEntity>)

    @Query("SELECT * FROM schedule_details WHERE fileId = :fileId LIMIT 1")
    suspend fun getDetailByFileId(fileId: String): ScheduleDetailEntity?

    @Query("UPDATE week_items SET isNotified = :notified WHERE id = :weekId")
    suspend fun updateWeekNotifiedStatus(weekId: String, notified: Boolean)
}

@Dao
interface FormDao {
    @Query("SELECT * FROM form_items")
    suspend fun getAllForms(): List<FormItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForms(forms: List<FormItemEntity>)

    @Query("DELETE FROM form_items")
    suspend fun clearForms()
}

@Database(entities = [WeekItemEntity::class, ScheduleDetailEntity::class, AlarmEntity::class, ClassGradeEntity::class, GradeSubjectEntity::class, FormItemEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun alarmDao(): AlarmDao
    abstract fun gradeDao(): GradeDao
    abstract fun formDao(): FormDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE week_items ADD COLUMN isNotified INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule-db"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { INSTANCE = it }
            }
        }
    }
}

