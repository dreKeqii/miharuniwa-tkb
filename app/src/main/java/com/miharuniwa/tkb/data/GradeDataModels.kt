package com.miharuniwa.tkb.data

import org.json.JSONObject

data class ParsedMark(
    val type: String,
    val coefficient: Int?,
    val date: String?,
    val score: Double
)

data class ParsedStudent(
    val sequenceNumber: String,
    val studentName: String,
    val birthDate: String,
    val marks: List<ParsedMark>,
    val finalScore: Double?
)

data class ParsedGradeSheet(
    val subjectName: String,
    val teacherName: String,
    val className: String,
    val examDate: String?,
    val students: List<ParsedStudent>
)

/**
 * Cấu trúc gom nhóm tất cả các môn của một học sinh
 */
data class AggregatedStudent(
    val studentName: String,
    val birthDate: String,
    val subjects: List<AggregatedSubjectGrade>
)

/**
 * Điểm một môn học của một học sinh
 */
data class AggregatedSubjectGrade(
    val subjectName: String,
    val teacherName: String,
    val className: String,
    val examDate: String?,
    val sequenceNumber: String, // STT của học sinh trong môn học này
    val marks: List<ParsedMark>,
    val finalScore: Double?
)

/**
 * Tiện ích bóc tách JSON
 */
object GradeJsonParser {
    fun parseJson(jsonStr: String): ParsedGradeSheet? {
        if (jsonStr.isBlank()) return null
        try {
            val cleanStr = jsonStr.trim()
            val root = if (cleanStr.startsWith("[")) {
                org.json.JSONArray(cleanStr).getJSONObject(0)
            } else {
                JSONObject(cleanStr)
            }
            val subjectName = root.optString("subjectName", "")
            val teacherName = root.optString("teacherName", "")
            val className = root.optString("className", "")
            val examDate = if (root.isNull("examDate")) null else root.optString("examDate", "")

            val studentsArray = root.getJSONArray("students")
            val studentsList = mutableListOf<ParsedStudent>()

            for (i in 0 until studentsArray.length()) {
                val sObj = studentsArray.getJSONObject(i)
                val seqNum = sObj.optString("sequenceNumber", "")
                val name = sObj.optString("studentName", "")
                val bDate = sObj.optString("birthDate", "")
                
                val marksArray = sObj.getJSONArray("marks")
                val marksList = mutableListOf<ParsedMark>()
                for (j in 0 until marksArray.length()) {
                    val mObj = marksArray.getJSONObject(j)
                    val type = mObj.optString("type", "")
                    val coef = if (mObj.isNull("coefficient")) null else mObj.optInt("coefficient")
                    val date = if (mObj.isNull("date")) null else mObj.optString("date", "")
                    val score = mObj.optDouble("score", 0.0)
                    marksList.add(ParsedMark(type, coef, date, score))
                }

                val finalScore = if (sObj.isNull("finalScore")) null else sObj.optDouble("finalScore")
                studentsList.add(ParsedStudent(seqNum, name, bDate, marksList, finalScore))
            }

            return ParsedGradeSheet(subjectName, teacherName, className, examDate, studentsList)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
