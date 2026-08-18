package com.miharuniwa.tkb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grade_subjects")
data class GradeSubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val classId: String,       // Khóa ngoại liên kết tới ClassGradeEntity.id
    val subjectName: String,   // Tên môn học / học phần bóc tách được
    val driveLink: String,     // Link iframe nhúng tệp điểm Google Drive
    val fileId: String,        // Google Drive File ID của tệp điểm PDF
    val jsonGrades: String? = null // Dữ liệu điểm chi tiết định dạng JSON sau khi Gemini phân tích
)
