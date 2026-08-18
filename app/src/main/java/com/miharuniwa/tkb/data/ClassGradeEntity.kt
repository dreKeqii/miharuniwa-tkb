package com.miharuniwa.tkb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_grades")
data class ClassGradeEntity(
    @PrimaryKey val id: String, // ID duy nhất trích xuất từ slug của URL lớp
    val className: String,      // Tên lớp học (ví dụ: "CĐ Lập trình máy tính K18")
    val link: String            // URL trang chi tiết bảng điểm của lớp
)
