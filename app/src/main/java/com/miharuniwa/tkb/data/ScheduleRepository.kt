package com.miharuniwa.tkb.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.Pattern
import android.content.Context
import android.net.Uri
import java.io.File
import android.app.DownloadManager
import android.os.Environment
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import com.miharuniwa.tkb.data.AggregatedStudent
import com.miharuniwa.tkb.data.AggregatedSubjectGrade
import com.miharuniwa.tkb.data.GradeJsonParser

class ScheduleRepository(private val dao: ScheduleDao, val alarmDao: AlarmDao, val gradeDao: GradeDao, val formDao: FormDao) {

    val parsingStates = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isParsingAll = kotlinx.coroutines.flow.MutableStateFlow<Boolean>(false)

    suspend fun getCachedWeeks(): List<WeekItemEntity> {
        return dao.getAllWeeks().map { it.copy(labelText = generateLabelText(it.title)) }
    }

    suspend fun fetchAndCacheWeeks(rootUrl: String): List<WeekItemEntity> {
        return withContext(Dispatchers.IO) {
            val document = Jsoup.connect(rootUrl).get()
            val links = document.select(".wp-block-post-title a")
            
            val cachedWeeks = dao.getAllWeeks().associateBy { it.id }
            val weeks = mutableListOf<WeekItemEntity>()
            val now = LocalDate.now()
            
            for (linkElement in links) {
                val title = linkElement.text()
                val url = linkElement.attr("href")
                val id = url.trimEnd('/').substringAfterLast('/')
                
                val isSatHach = title.contains("sát hạch", ignoreCase = true)
                val labelText = generateLabelText(title)
                
                val isNotified = cachedWeeks[id]?.isNotified ?: false
                
                weeks.add(WeekItemEntity(id, title, url, isSatHach, labelText, isNotified))
            }
            
            if (weeks.isNotEmpty()) {
                dao.clearWeeks()
                dao.insertWeeks(weeks)
            }
            weeks
        }
    }

    suspend fun getCachedDetails(weekId: String): List<ScheduleDetailEntity> {
        return dao.getDetailsForWeek(weekId).distinctBy { "${it.base}|${it.systemType}" }
    }

    suspend fun markWeekAsNotified(weekId: String) {
        withContext(Dispatchers.IO) {
            dao.updateWeekNotifiedStatus(weekId, true)
        }
    }

    suspend fun fetchAndCacheDetails(weekId: String, url: String): List<ScheduleDetailEntity> {
        return withContext(Dispatchers.IO) {
            val document = Jsoup.connect(url).get()
            val details = mutableListOf<ScheduleDetailEntity>()
            
            val contentElements = document.select(".wp-block-post-content p")
            
            var currentBase = ""
            var currentType = ""
            
            for (element in contentElements) {
                val text = element.text().trim()
                if (element.select("strong").isNotEmpty() && text.contains("Thời khóa biểu", ignoreCase = true)) {
                    // Extract base
                    val baseMatch = Regex("(CS1|CS2)").find(text)
                    currentBase = baseMatch?.value ?: "Khác"
                    
                    // Extract type
                    currentType = text
                        .replace("Thời khóa biểu các lớp", "", ignoreCase = true)
                        .replace("học tại CS1", "", ignoreCase = true)
                        .replace("học tại CS2", "", ignoreCase = true)
                        .replace("lớp", "", ignoreCase = true)
                        .trim()
                } else if (element.select("iframe").isNotEmpty()) {
                    val iframeSrc = element.select("iframe").attr("src")
                    if (iframeSrc.isNotEmpty() && iframeSrc.contains("drive.google.com/file/d/")) {
                        val fileId = iframeSrc.split("/d/")[1].split("/")[0]
                        if (currentType.isNotEmpty()) {
                            details.add(ScheduleDetailEntity(
                                weekId = weekId,
                                base = currentBase,
                                systemType = currentType,
                                driveLink = iframeSrc,
                                fileId = fileId
                            ))
                        }
                        currentBase = ""
                        currentType = ""
                    }
                }
            }
            
            if (details.isNotEmpty()) {
                dao.clearDetailsForWeek(weekId)
                dao.insertDetails(details)
            }
            details
        }
    }

    private fun generateLabelText(title: String): String {
        if (title.contains("sát hạch", ignoreCase = true)) {
            return "Lịch sát hạch lái xe"
        }
        val range = ScheduleUtils.parseWeekRange(title)
        if (range != null) {
            val now = LocalDate.now()
            val status = when {
                now.isBefore(range.startDate) -> "Tuần tiếp"
                now.isAfter(range.endDate) -> "Tuần trước"
                else -> "Tuần hiện tại"
            }
            val outFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val outStartStr = range.startDate.format(outFormatter)
            val outEndStr = range.endDate.format(outFormatter)
            
            return "[$status] | $outStartStr - $outEndStr"
        }
        return title
    }


    suspend fun downloadPdf(fileId: String, context: Context, checkUpdate: Boolean = true): Uri? {
        return withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "schedule_$fileId.pdf")
            val downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
            val hasOffline = file.exists() && file.length() > 0

            if (hasOffline) {
                if (checkUpdate) {
                    // Chạy ngầm kiểm tra cập nhật mà không chặn hiển thị
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val client = OkHttpClient()
                            val checkRequest = Request.Builder().url(downloadUrl).head().build()
                            client.newCall(checkRequest).execute().use { response ->
                                if (response.isSuccessful) {
                                    val onlineSize = response.header("Content-Length")?.toLongOrNull() ?: -1L
                                    if (onlineSize > 0 && onlineSize != file.length()) {
                                        val downloadRequest = Request.Builder().url(downloadUrl).build()
                                        client.newCall(downloadRequest).execute().use { dlResponse ->
                                            if (dlResponse.isSuccessful) {
                                                val bytes = dlResponse.body?.bytes()
                                                if (bytes != null) {
                                                    file.writeBytes(bytes)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                return@withContext Uri.fromFile(file)
            }

            try {
                val request = Request.Builder().url(downloadUrl).build()
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                
                val bytes = response.body?.bytes() ?: return@withContext null
                file.writeBytes(bytes)
                Uri.fromFile(file)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun savePdfToDownloads(context: Context, fileId: String, subjectName: String) {
        try {
            val downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
            val cleanName = subjectName.replace(Regex("[^a-zA-Z0-9 \\-_]"), "")
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Bảng điểm $cleanName")
                .setDescription("Đang tải bảng điểm môn $cleanName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "tkb_$cleanName.pdf")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Đã bắt đầu tải file xuống thư mục Download", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Lỗi khi tải file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun parsePdfWithGemini(
        context: Context,
        fileId: String,
        apiKey: String,
        modelName: String,
        trackedClasses: List<String> = emptyList()
    ): String? {
        val uri = downloadPdf(fileId, context) ?: return null
        val client = GeminiClient(okhttp3.OkHttpClient())
        return try {
            client.parseScheduleFromPdf(context, uri, apiKey, modelName, trackedClasses)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCachedClassGrades(): List<ClassGradeEntity> {
        return gradeDao.getAllClassGrades()
    }

    suspend fun fetchAndCacheAllClassGrades(rootUrl: String, shouldClear: Boolean = false) {
        withContext(Dispatchers.IO) {
            val document = Jsoup.connect(rootUrl).get()
            val links = document.select(".wp-block-post-title a")
            val classes = mutableListOf<ClassGradeEntity>()
            for (linkElement in links) {
                val title = linkElement.text().trim()
                val url = linkElement.attr("href")
                val id = url.trimEnd('/').substringAfterLast('/')
                classes.add(ClassGradeEntity(id, title, url))
            }
            
            val pageLinks = document.select("a.page-numbers")
            if (pageLinks.isNotEmpty()) {
                val maxPage = pageLinks.mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
                if (maxPage > 1) {
                    val anyHref = pageLinks.map { it.attr("href") }.find { it.contains("query-") } ?: ""
                    val queryMatch = Regex("query-(\\d+)").find(anyHref)
                    val queryId = queryMatch?.groupValues?.get(1)
                    
                    if (queryId != null) {
                        val baseWithoutQuery = rootUrl.substringBefore("?")
                        val cleanBase = if (baseWithoutQuery.endsWith("/")) baseWithoutQuery else "$baseWithoutQuery/"
                        
                        val deferreds = (2..maxPage).map { page ->
                            async {
                                try {
                                    val pageUrl = "${cleanBase}?query-$queryId-page=$page"
                                    val pageDoc = Jsoup.connect(pageUrl).get()
                                    val pLinks = pageDoc.select(".wp-block-post-title a")
                                    pLinks.map { link ->
                                        val title = link.text().trim()
                                        val url = link.attr("href")
                                        val id = url.trimEnd('/').substringAfterLast('/')
                                        ClassGradeEntity(id, title, url)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    emptyList()
                                }
                            }
                        }
                        classes.addAll(deferreds.awaitAll().flatten())
                    }
                }
            }

            if (classes.isNotEmpty()) {
                if (shouldClear) {
                    gradeDao.clearClassGrades()
                }
                gradeDao.insertClassGrades(classes)
            }
        }
    }

    suspend fun getCachedGradeSubjects(classId: String): List<GradeSubjectEntity> {
        return gradeDao.getSubjectsForClass(classId)
    }

    suspend fun fetchAndCacheGradeSubjects(classId: String, url: String): List<GradeSubjectEntity> {
        return withContext(Dispatchers.IO) {
            val document = Jsoup.connect(url).get()
            val subjects = mutableListOf<GradeSubjectEntity>()
            val postContent = document.selectFirst(".wp-block-post-content")
            if (postContent != null) {
                val children = postContent.children()
                var currentSubjectName = ""
                var index = 1
                for (element in children) {
                    val text = element.text().trim()
                    if (element.tagName() == "p" && element.select("strong").isNotEmpty()) {
                        currentSubjectName = text
                    }
                    val iframes = element.select("iframe")
                    for (iframe in iframes) {
                        val iframeSrc = iframe.attr("src")
                        if (iframeSrc.isNotEmpty() && (iframeSrc.contains("drive.google.com/file/d/") || iframeSrc.contains("preview.html") || iframeSrc.contains("preview"))) {
                            val fileId = if (iframeSrc.contains("/d/")) {
                                iframeSrc.split("/d/")[1].split("/")[0]
                            } else {
                                "demo_file_${classId}_$index"
                            }
                            val displayName = if (currentSubjectName.isNotEmpty()) {
                                currentSubjectName
                                    .replace("Bảng điểm", "", ignoreCase = true)
                                    .replace("lớp", "", ignoreCase = true)
                                    .trim()
                            } else {
                                "Môn học học phần $index"
                            }
                            subjects.add(GradeSubjectEntity(
                                classId = classId,
                                subjectName = displayName,
                                driveLink = iframeSrc,
                                fileId = fileId
                            ))
                            index++
                            currentSubjectName = ""
                        }
                    }
                }
            }
            if (subjects.isNotEmpty()) {
                val oldSubjects = gradeDao.getSubjectsForClass(classId)
                val mergedSubjects = subjects.map { newSubject ->
                    val old = oldSubjects.find { it.fileId == newSubject.fileId }
                    if (old != null) {
                        newSubject.copy(
                            jsonGrades = old.jsonGrades,
                            subjectName = if (old.jsonGrades != null) old.subjectName else newSubject.subjectName
                        )
                    } else {
                        newSubject
                    }
                }
                gradeDao.clearSubjectsForClass(classId)
                gradeDao.insertGradeSubjects(mergedSubjects)
                return@withContext mergedSubjects
            }
            subjects
        }
    }

    suspend fun parseGradesWithGemini(
        context: Context,
        fileId: String,
        apiKey: String,
        modelName: String
    ): String? {
        val uri = downloadPdf(fileId, context, checkUpdate = false) ?: return null
        val client = GeminiClient(okhttp3.OkHttpClient())
        return try {
            val jsonResult = client.parseGradesFromPdf(context, uri, apiKey, modelName, fileId)
            if (jsonResult.isNotEmpty()) {
                gradeDao.updateGradesForSubject(fileId, jsonResult)
                try {
                    val cleanResult = jsonResult.trim()
                    val jsonObject = if (cleanResult.startsWith("[")) {
                        org.json.JSONArray(cleanResult).getJSONObject(0)
                    } else {
                        org.json.JSONObject(cleanResult)
                    }
                    val realSubjectName = jsonObject.optString("subjectName", "")
                    if (realSubjectName.isNotEmpty()) {
                        val subject = gradeDao.getSubjectByFileId(fileId)
                        if (subject != null) {
                            gradeDao.insertGradeSubjects(listOf(subject.copy(subjectName = realSubjectName, jsonGrades = jsonResult)))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            jsonResult
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    suspend fun getCachedForms(): List<FormItemEntity> {
        return formDao.getAllForms()
    }

    suspend fun fetchAndCacheForms(url: String): List<FormItemEntity> {
        return withContext(Dispatchers.IO) {
            val document = Jsoup.connect(url).get()
            // Tìm tất cả các thẻ h3 chứa link google drive
            val linkElements = document.select("h3 a[href*=drive.google.com/file/d/]")
            
            val forms = mutableListOf<FormItemEntity>()
            for (aElement in linkElements) {
                val driveLink = aElement.attr("href")
                // Viết hoa chữ cái đầu của mỗi từ
                val rawTitle = aElement.text().trim()
                val title = rawTitle.split(" ").joinToString(" ") { word -> 
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } 
                }
                
                if (title.isNotEmpty() && driveLink.isNotEmpty()) {
                    val fileId = try {
                        driveLink.split("/d/")[1].split("/")[0]
                    } catch (e: Exception) {
                        ""
                    }
                    if (fileId.isNotEmpty()) {
                        forms.add(FormItemEntity(id = fileId, title = title, driveLink = driveLink, fileId = fileId))
                    }
                }
            }
            
            val uniqueForms = forms.distinctBy { it.fileId }
            if (uniqueForms.isNotEmpty()) {
                formDao.clearForms()
                formDao.insertForms(uniqueForms)
            }
            uniqueForms
        }
    }

    suspend fun fetchHandbookLink(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val document = Jsoup.connect(url).get()
                // Find h2.wp-block-heading then look for the first iframe after it
                val h2s = document.select("h2.wp-block-heading")
                for (h2 in h2s) {
                    var next = h2.nextElementSibling()
                    while (next != null && next.tagName() != "h2") {
                        if (next.tagName() == "figure" || next.tagName() == "div" || next.tagName() == "p") {
                            val iframe = next.selectFirst("iframe")
                            if (iframe != null) {
                                val src = iframe.attr("src")
                                if (src.contains("drive.google.com/file/d/")) {
                                    return@withContext src
                                }
                            }
                        }
                        if (next.tagName() == "iframe") {
                            val src = next.attr("src")
                            if (src.contains("drive.google.com/file/d/")) {
                                return@withContext src
                            }
                        }
                        next = next.nextElementSibling()
                    }
                }
                
                // Fallback: just find any iframe with drive link
                val anyIframe = document.selectFirst("iframe[src*=drive.google.com/file/d/]")
                anyIframe?.attr("src")
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getAggregatedStudents(): List<AggregatedStudent> {
        return withContext(Dispatchers.IO) {
            val parsedSubjects = gradeDao.getParsedSubjects()
            val studentMap = mutableMapOf<String, MutableList<AggregatedSubjectGrade>>()

            parsedSubjects.forEach { subject ->
                val jsonStr = subject.jsonGrades
                if (!jsonStr.isNullOrBlank()) {
                    val parsedSheet = GradeJsonParser.parseJson(jsonStr)
                    if (parsedSheet != null) {
                        parsedSheet.students.forEach { student ->
                            val key = "${student.studentName}_${student.birthDate}"
                            if (!studentMap.containsKey(key)) {
                                studentMap[key] = mutableListOf()
                            }
                            studentMap[key]?.add(
                                AggregatedSubjectGrade(
                                    subjectName = parsedSheet.subjectName.ifBlank { subject.subjectName },
                                    teacherName = parsedSheet.teacherName,
                                    className = parsedSheet.className,
                                    examDate = parsedSheet.examDate,
                                    sequenceNumber = student.sequenceNumber,
                                    marks = student.marks,
                                    finalScore = student.finalScore
                                )
                            )
                        }
                    }
                }
            }

            studentMap.map { (key, subjectList) ->
                val parts = key.split("_", limit = 2)
                val name = parts.getOrElse(0) { "" }
                val birthDate = parts.getOrElse(1) { "" }
                AggregatedStudent(
                    studentName = name,
                    birthDate = birthDate,
                    subjects = subjectList
                )
            }.sortedBy { it.studentName }
        }
    }
}
