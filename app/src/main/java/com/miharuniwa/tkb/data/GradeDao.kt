package com.miharuniwa.tkb.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GradeDao {
    @Query("SELECT * FROM class_grades")
    suspend fun getAllClassGrades(): List<ClassGradeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassGrades(classes: List<ClassGradeEntity>)

    @Query("DELETE FROM class_grades")
    suspend fun clearClassGrades()

    @Query("SELECT * FROM grade_subjects WHERE classId = :classId")
    suspend fun getSubjectsForClass(classId: String): List<GradeSubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGradeSubjects(subjects: List<GradeSubjectEntity>)

    @Query("DELETE FROM grade_subjects WHERE classId = :classId")
    suspend fun clearSubjectsForClass(classId: String)

    @Query("UPDATE grade_subjects SET jsonGrades = :jsonGrades WHERE fileId = :fileId")
    suspend fun updateGradesForSubject(fileId: String, jsonGrades: String)

    @Query("SELECT * FROM grade_subjects WHERE fileId = :fileId LIMIT 1")
    suspend fun getSubjectByFileId(fileId: String): GradeSubjectEntity?

    @Query("SELECT * FROM grade_subjects WHERE jsonGrades IS NOT NULL AND jsonGrades != ''")
    suspend fun getParsedSubjects(): List<GradeSubjectEntity>
}
