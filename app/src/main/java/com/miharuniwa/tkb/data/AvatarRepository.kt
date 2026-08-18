package com.miharuniwa.tkb.data

import android.content.Context
import android.content.SharedPreferences

class AvatarRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("avatar_prefs", Context.MODE_PRIVATE)

    /**
     * Tạo khóa (key) dựa trên tên và ngày sinh của học sinh để đảm bảo tính duy nhất.
     */
    private fun generateKey(studentName: String, birthDate: String): String {
        return "${studentName}_${birthDate}".replace(" ", "_")
    }

    /**
     * Lưu đường dẫn (URI) của ảnh đại diện vào SharedPreferences.
     */
    fun saveAvatarUri(studentName: String, birthDate: String, uriString: String) {
        val key = generateKey(studentName, birthDate)
        prefs.edit().putString(key, uriString).apply()
    }

    /**
     * Lấy đường dẫn (URI) của ảnh đại diện từ SharedPreferences.
     * Trả về null nếu chưa có ảnh nào được lưu.
     */
    fun getAvatarUri(studentName: String, birthDate: String): String? {
        val key = generateKey(studentName, birthDate)
        return prefs.getString(key, null)
    }
}
