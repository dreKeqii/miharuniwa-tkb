package com.miharuniwa.tkb.data

import org.json.JSONArray
import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Trung tâm logic dùng chung cho toàn app.
 * Thay thế 3 bản copy parseSchedule, 2 bản getDayOfWeekString, v.v.
 */

data class ScheduleItem(
    val className: String,
    val subject: String,
    val dayOfWeek: String,
    val date: String = "",
    val session: String,
    val room: String,
    val teacher: String = "",
    val classSize: String? = ""
)

object ScheduleUtils {

    data class WeekRange(val startDate: LocalDate, val endDate: LocalDate)

    fun parseWeekRange(title: String): WeekRange? {
        val datePattern = Pattern.compile("từ\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+đến\\s+(\\d{1,2}/\\d{1,2}/\\d{4})", Pattern.CASE_INSENSITIVE)
        val matcher = datePattern.matcher(title)
        if (matcher.find()) {
            val startStr = matcher.group(1)
            val endStr = matcher.group(2)
            val formatter = DateTimeFormatter.ofPattern("d/M/yyyy")
            return try {
                WeekRange(
                    startDate = LocalDate.parse(startStr, formatter),
                    endDate = LocalDate.parse(endStr, formatter)
                )
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun getTargetWeek(weeks: List<WeekItemEntity>, now: LocalDate = LocalDate.now()): WeekItemEntity? {
        if (weeks.isEmpty()) return null
        
        val cal = Calendar.getInstance()
        val referenceDate = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            now.plusDays(1)
        } else {
            now
        }

        // 1. Tìm tuần bao phủ referenceDate
        val matchingWeek = weeks.find { week ->
            val range = parseWeekRange(week.title)
            if (range != null) {
                !referenceDate.isBefore(range.startDate) && !referenceDate.isAfter(range.endDate)
            } else {
                false
            }
        }
        if (matchingWeek != null) return matchingWeek

        // 2. Nếu không tìm thấy, lấy tuần có startDate lớn nhất (mới nhất)
        return weeks.maxByOrNull { week ->
            parseWeekRange(week.title)?.startDate ?: LocalDate.MIN
        }
    }


    fun cleanClassName(name: String): String {
        return name.replace(Regex("\\s*\\(\\d+\\s*(SV|HV|sv|hv)\\)\\s*$", RegexOption.IGNORE_CASE), "").trim()
    }

    fun parseScheduleJson(jsonStr: String): List<ScheduleItem> {
        val list = mutableListOf<ScheduleItem>()
        try {
            // Loại bỏ các block markdown json nếu có
            val cleanJsonStr = Regex("\\[.*\\]", RegexOption.DOT_MATCHES_ALL).find(jsonStr)?.value ?: jsonStr
            val arr = JSONArray(cleanJsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ScheduleItem(
                        className = cleanClassName(obj.optString("className", "")),
                        subject = obj.optString("subject", ""),
                        dayOfWeek = obj.optString("dayOfWeek", ""),
                        date = obj.optString("date", ""),
                        session = obj.optString("session", ""),
                        room = obj.optString("room", ""),
                        teacher = obj.optString("teacher", ""),
                        classSize = obj.optString("classSize", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getDayOfWeekString(day: Int): String {
        return when (day) {
            Calendar.MONDAY -> "Thứ 2"
            Calendar.TUESDAY -> "Thứ 3"
            Calendar.WEDNESDAY -> "Thứ 4"
            Calendar.THURSDAY -> "Thứ 5"
            Calendar.FRIDAY -> "Thứ 6"
            Calendar.SATURDAY -> "Thứ 7"
            Calendar.SUNDAY -> "CN"
            else -> ""
        }
    }

    fun getDayOrder(day: String): Int {
        return when (day) {
            "Thứ 2" -> 2
            "Thứ 3" -> 3
            "Thứ 4" -> 4
            "Thứ 5" -> 5
            "Thứ 6" -> 6
            "Thứ 7" -> 7
            "CN", "Chủ Nhật", "Chủ nhật" -> 8
            else -> 9
        }
    }

    fun getSessionOrder(session: String): Int {
        return when (session) {
            "Sáng" -> 1
            "Chiều" -> 2
            "Tối" -> 3
            else -> 4
        }
    }

    fun formatRoom(room: String): String {
        if (room.isEmpty()) return ""
        val cleanRoom = room.replace(Regex("^(P\\.|P|p\\.|p)\\s*", RegexOption.IGNORE_CASE), "")
        return "Phòng: $cleanRoom"
    }

    fun extractClassNames(jsonStr: String): List<String> {
        return parseScheduleJson(jsonStr)
            .map { it.className }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun filterByClassAndDay(items: List<ScheduleItem>, className: String, dayOfWeek: String): List<ScheduleItem> {
        return items.filter { it.className == className && it.dayOfWeek == dayOfWeek }
            .sortedBy { getSessionOrder(it.session) }
    }

    fun sortScheduleItems(items: List<ScheduleItem>): List<ScheduleItem> {
        return items.sortedWith(compareBy(
            { item -> 
                if (item.date.isNotEmpty()) {
                    try {
                        LocalDate.parse(item.date, DateTimeFormatter.ofPattern("d/M/yyyy")).toEpochDay()
                    } catch (e: Exception) {
                        0L
                    }
                } else {
                    0L
                }
            },
            { getDayOrder(it.dayOfWeek) },
            { getSessionOrder(it.session) }
        ))
    }

    /**
     * Logic gộp mảng JSON:
     * - oldJsonStr: Mảng JSON cũ đang lưu trong database.
     * - newItems: Danh sách môn học tuần mới vừa phân tích được.
     * - fetchedWeeks: Các tuần hợp lệ đang tồn tại trên website trường (vd: tuan1 và tuan2).
     * - targetWeek: Tuần vừa mới được phân tích (là một trong các fetchedWeeks).
     */
    fun mergeSchedules(
        oldJsonStr: String,
        newItems: List<ScheduleItem>,
        fetchedWeeks: List<WeekItemEntity>,
        targetWeek: WeekItemEntity
    ): String {
        val oldItems = parseScheduleJson(oldJsonStr)

        // 1. Áo giáp an toàn: Nếu phân tích ra mảng rỗng (PDF lỗi hoặc không có môn nào),
        // tuyệt đối KHÔNG ĐƯỢC xoá dữ liệu cũ của tuần này. Trả về y nguyên DB hiện tại.
        if (newItems.isEmpty()) {
            return oldJsonStr
        }

        val targetRange = parseWeekRange(targetWeek.title)
        val validRanges = fetchedWeeks.mapNotNull { parseWeekRange(it.title) }

        val filteredOldItems = oldItems.filter { item ->
            try {
                val itemDate = if (item.date.isNotEmpty()) LocalDate.parse(item.date, DateTimeFormatter.ofPattern("d/M/yyyy")) else null

                if (itemDate != null) {
                    // 1. Xoá đúng mục tiêu: Nếu ngày nằm trong tuần đang yêu cầu phân tích (targetWeek) -> Xoá
                    if (targetRange != null && !itemDate.isBefore(targetRange.startDate) && !itemDate.isAfter(targetRange.endDate)) {
                        return@filter false
                    }

                    // 2. Dọn rác quá khứ: Nếu ngày cũ hơn TẤT CẢ các tuần hiện có trên Web -> Xoá
                    val minStartDate = validRanges.minOfOrNull { it.startDate }
                    if (minStartDate != null && itemDate.isBefore(minStartDate)) {
                        return@filter false
                    }

                    true
                } else {
                    // Xử lý khi dữ liệu cũ đéo có ngày (trường hợp hiếm nhưng vẫn phải lo):
                    // Chỉ xoá đi nếu trong mảng mới CÓ lịch đè lên ĐÚNG Lớp, Thứ, Buổi đó.
                    val isOverwritten = newItems.any { it.className == item.className && it.dayOfWeek == item.dayOfWeek && it.session == item.session }
                    !isOverwritten
                }
            } catch (e: Exception) {
                true // Lỗi định dạng ngày thì cứ giữ lại cho chắc, không xoá bậy
            }
        }

        // Nối mảng và loại bỏ các bản ghi trùng lặp 100% (chống bug trường đăng 1 PDF lên cả 2 bài)
        val mergedItems = (filteredOldItems + newItems).distinctBy { 
            "${it.className}_${it.dayOfWeek}_${it.date}_${it.session}_${it.subject}_${it.room}"
        }

        // Chuyển lại thành chuỗi JSON
        val jsonArray = JSONArray()
        for (item in mergedItems) {
            val obj = org.json.JSONObject()
            obj.put("className", item.className)
            obj.put("subject", item.subject)
            obj.put("dayOfWeek", item.dayOfWeek)
            obj.put("date", item.date)
            obj.put("session", item.session)
            obj.put("room", item.room)
            obj.put("teacher", item.teacher)
            obj.put("classSize", item.classSize)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}
