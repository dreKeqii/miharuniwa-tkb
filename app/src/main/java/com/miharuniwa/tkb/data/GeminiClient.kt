package com.miharuniwa.tkb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class GeminiModel(
    val name: String,
    val version: String,
    val displayName: String,
    val description: String,
    val inputTokenLimit: Int,
    val outputTokenLimit: Int,
    val supportedGenerationMethods: List<String>
)

@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModel>
)

class GeminiClient(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchModels(apiKey: String): List<GeminiModel> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch models: ${response.code}")
            val bodyString = response.body?.string() ?: throw IOException("Empty response body")
            val parsedResponse = json.decodeFromString<GeminiModelListResponse>(bodyString)
            // Lọc ra các model hỗ trợ generateContent, và chỉ lấy model gemini/gemma, ưu tiên gemma lên đầu
            parsedResponse.models.filter { 
                it.supportedGenerationMethods.contains("generateContent") &&
                (it.name.contains("gemini", ignoreCase = true) || it.name.contains("gemma", ignoreCase = true))
            }.sortedByDescending { it.name.contains("gemma", ignoreCase = true) }
        }
    }

    suspend fun parseScheduleFromPdf(
        context: Context,
        pdfUri: Uri,
        apiKey: String,
        modelName: String,
        trackedClasses: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val bitmaps = pdfToBitmaps(context, pdfUri)
        if (bitmaps.isEmpty()) throw IOException("Could not read any pages from PDF")

        val client = Client.builder().apiKey(apiKey).build()
        
        // Cấu hình có Thinking Mode mức MINIMAL để tối ưu tốc độ phân tích ngầm
        val thinkingConfigObj = ThinkingConfig.builder().thinkingLevel("HIGH").build()
        val configWithThinking = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .thinkingConfig(thinkingConfigObj)
            .build()
            
        // Fallback config nếu model không hỗ trợ ThinkingConfig
        val defaultConfig = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .build()

        val trackedClassesStr = if (trackedClasses.isNotEmpty()) {
            "Danh sách các Lớp chính đang được theo dõi: ${trackedClasses.joinToString(", ")}. "
        } else ""

        val prompt = """
            Bạn là một trợ lý ảo chuyên nghiệp. Nhiệm vụ của bạn là phân tích hình ảnh thời khóa biểu và trích xuất thông tin lịch học thành mảng JSON. 
            Lưu ý: Bảng thời khóa biểu thường có các cột là Thứ trong tuần, và các hàng được chia theo Buổi học (Sáng/Chiều) và Tên Lớp. Một lớp có thể kéo dài qua nhiều buổi học. Hãy chú ý gióng hàng và cột cho chính xác.

            ${trackedClassesStr}Quy tắc bóc tách đặc biệt:
            - Nắn lại tên lớp (Dirty Data): Nếu trong ảnh, Tên lớp có kèm sĩ số sinh viên/học viên (ví dụ "(5 SV)", "(04 SV)", "(07 HV)") thì bạn PHẢI tự động gạt bỏ sĩ số sinh viên/học viên đó ra khỏi trường className, chỉ giữ lại tên lớp trơn.
            - Hãy đối chiếu với Danh sách các Lớp chính ở trên (nếu có), nếu phát hiện lớp trong ảnh viết na ná/giống lớp đang theo dõi, hãy trả về CHÍNH XÁC tên lớp đang theo dõi (đã loại bỏ sĩ số).
            - Trích xuất sĩ số của lớp vào trường classSize riêng biệt (ví dụ: "(5 SV)" -> "5", "(07 HV)" -> "7"). Nếu không có thông tin sĩ số, trả về chuỗi rỗng "".

            Quy tắc bóc tách:
            1. Đọc kỹ bảng từ trái qua phải, từ trên xuống dưới.
            2. Với mỗi ô có chứa lịch học của một môn, hãy tạo một đối tượng JSON. Nếu một ô trống, hãy bỏ qua.
            3. Gióng theo hàng và cột để lấy đúng: Lớp (ở cột LỚP), Buổi học (Sáng/Chiều ở cột BUỔI), và Thứ trong tuần (ở các cột THỨ).
            4. Trong ô nội dung môn học, thông thường dòng đầu là Tên môn học, dòng giữa là Tên giáo viên, và dòng cuối là Phòng học. Hãy phân tách chúng chính xác.
            5. Định dạng JSON yêu cầu là một mảng (Array).

            Cấu trúc JSON bắt buộc:
            [
                {
                "className": "Tên lớp trơn đã làm sạch sĩ số (ví dụ: CĐ LT MTT K1, CĐ LT QTKS K1)",
                "classSize": "Sĩ số của lớp trích xuất được (ví dụ: 5, 4, 7), nếu không có trả về chuỗi rỗng",
                "subject": "Tên môn học",
                "dayOfWeek": "Thứ 2" | "Thứ 3" | "Thứ 4" | "Thứ 5" | "Thứ 6" | "Thứ 7" | "CN",
                "date": "Ngày tháng năm trích xuất trực tiếp từ tiêu đề cột tương ứng (Ví dụ: 08/06/2026). BẮT BUỘC định dạng dd/MM/yyyy với năm đầy đủ 4 số.",
                "session": "Buổi học (Sáng/Chiều/Tối)",
                "room": "Phòng học sạch sẽ, chỉ trả về số phòng hoặc tên phòng, KHÔNG bao gồm tiếp đầu ngữ P, P., p. hay Phòng (ví dụ: 'P. 2.8' -> '2.8', 'P. Thực hành' -> 'Thực hành', 'Xưởng may' -> 'Xưởng may')",
                "teacher": "Tên giáo viên"
                }
            ]
        """.trimIndent()

        // Create the contents list (Prompt + all Bitmaps)
        val parts = mutableListOf<Part>()
        parts.add(Part.fromText(prompt))
        
        for (bitmap in bitmaps) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val bytes = stream.toByteArray()
            parts.add(Part.fromBytes(bytes, "image/jpeg"))
        }

        val content = Content.builder().parts(parts).build()

        val response = try {
            client.models.generateContent(modelName, content, configWithThinking)
        } catch (e: Exception) {
            // Fallback nếu model không hỗ trợ ThinkingConfig (ví dụ 400 Bad Request)
            client.models.generateContent(modelName, content, defaultConfig)
        }

        response.text() ?: throw IOException("Empty response from AI")
    }

    suspend fun parseGradesFromPdf(
        context: Context,
        pdfUri: Uri,
        apiKey: String,
        modelName: String,
        fileId: String = ""
    ): String = withContext(Dispatchers.IO) {
        val bitmaps = pdfToBitmaps(context, pdfUri)
        if (bitmaps.isEmpty()) throw IOException("Could not read any pages from PDF")

        val client = Client.builder().apiKey(apiKey).build()
        
        val thinkingConfigObj = ThinkingConfig.builder().thinkingLevel("HIGH").build()
        val configWithThinking = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .thinkingConfig(thinkingConfigObj)
            .build()
            
        val defaultConfig = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .build()

        val prompt = """
            Bạn là một trợ lý AI chuyên nghiệp về thị giác máy tính và bóc tách dữ liệu học thuật. Nhiệm vụ của bạn là đọc các trang ảnh chụp bảng ghi điểm học phần và trích xuất thông tin thành cấu trúc JSON.

            Lưu ý quan trọng:
            1. Bảng điểm chứa cả chữ viết in và chữ/số viết tay (đặc biệt ở cột điểm thi kết thúc môn và ngày thi viết tay ở header). Hãy nhận diện và đọc thật kỹ các nét viết tay này.
            2. Số lượng cột kiểm tra thường xuyên (TX) và kiểm tra định kỳ (DK) là thay đổi động tùy theo môn học và số tín chỉ. Hãy tự động nhận diện tất cả các cột điểm kiểm tra thực tế có trong bảng.
            3. Không được tự ý tính toán hay đoán mò các cột điểm bị trống (như cột điểm tổng kết ĐTK môn học nếu để trống).

            Hãy trích xuất các thông tin sau:
            - Tên môn học: nằm sau chữ "MÔN HỌC/ MÔ ĐUN:" ở phần đầu trang.
            - Tên giáo viên giảng dạy: nằm sau chữ "Giáo viên giảng dạy:".
            - Tên lớp: nằm sau chữ "Lớp:".
            - Ngày thi (ngày kiểm tra kết thúc môn): là ngày viết tay nằm sau chữ "Ngày kiểm tra kết thúc môn học/mô đun:". Trích xuất theo định dạng DD/MM/YYYY.

            Thông tin chi tiết của từng sinh viên trong bảng điểm:
            - Họ tên sinh viên: kết hợp chính xác từ cột "Họ và tên" (vốn được chia làm 2 cột nhỏ là Họ đệm và Tên).
            - Ngày sinh: trích xuất từ cột "Năm sinh" (cột này thực chất chứa đầy đủ ngày/tháng/năm sinh). ĐỊNH DẠNG BẮT BUỘC TRẢ VỀ: DD/MM/YYYY (ví dụ: ngày 9 tháng 7 năm 2005 thì bắt buộc phải trả về "09/07/2005", ngày 21 tháng 12 năm 2005 thì phải trả về "21/12/2005"). Ngày và tháng luôn phải có đủ 2 chữ số (thêm số 0 ở trước nếu là hàng đơn vị).
            - Danh sách điểm thành phần (marks): Với mỗi cột điểm kiểm tra phát hiện được trong bảng (bao gồm điểm Thường xuyên TX, điểm Định kỳ DK và điểm Thi kết thúc môn KT):
                + type: "TX" (điểm kiểm tra thường xuyên), "DK" (điểm kiểm tra định kỳ), "KT" (điểm kết thúc môn/điểm thi viết tay), hoặc "TK" (điểm tổng kết môn học/mô đun).
                + coefficient: 1 đối với TX, 2 đối với DK, hoặc null đối với KT.
                + date: Ngày thi/kiểm tra ghi ở đầu mỗi cột điểm tương ứng (ví dụ: "7/4", "24/3", "10/4"...) nếu có, nếu không ghi thì trả về null.
                + score: Giá trị điểm số thực tế đọc được dưới dạng số thực (Float) (ví dụ: 7.0, 6.5, 7.5). Nếu ô điểm trống, bỏ qua không đưa vào danh sách.
            - Điểm tổng kết môn học (finalScore): Trích xuất giá trị số ở cột "ĐTK MÔN HỌC/MÔ ĐUN". Nếu cột này trống hoặc không có số, trả về null.

            Hãy luôn bổ sung một trường "fileId" ở lớp gốc của JSON trả về, giá trị của trường này bắt buộc là "$fileId".

            Cấu trúc JSON bắt buộc phải trả về:
            {
                "fileId": "$fileId",
                "subjectName": "Tên môn học bóc tách được",
                "teacherName": "Tên giáo viên",
                "className": "Tên lớp học",
                "examDate": "Ngày thi kết thúc môn học (DD/MM/YYYY hoặc null)",
                "students": [
                {
                    "sequenceNumber": "Số thứ tự dạng chuỗi (ví dụ: '01', '02')",
                    "studentName": "Họ và tên sinh viên đầy đủ",
                    "birthDate": "Ngày sinh dạng DD/MM/YYYY (đầy đủ 2 chữ số ngày/tháng)",
                    "marks": [
                    {
                        "type": "TX" | "DK" | "KT", "TK",
                        "coefficient": 1 | 2 | null,
                        "date": "Ngày kiểm tra hoặc null",
                        "score": Điểm số dạng Float
                    }
                    ],
                    "finalScore": Điểm tổng kết dạng Float hoặc null
                }
                ]
            }
        """.trimIndent()

        val parts = mutableListOf<Part>()
        parts.add(Part.fromText(prompt))
        
        for (bitmap in bitmaps) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val bytes = stream.toByteArray()
            parts.add(Part.fromBytes(bytes, "image/jpeg"))
        }

        val content = Content.builder().parts(parts).build()

        val response = try {
            client.models.generateContent(modelName, content, configWithThinking)
        } catch (e: Exception) {
            throw IOException("Lỗi gọi Gemini API (có thể do cấu hình model): ${e.message}", e)
        }

        response.text() ?: throw IOException("Empty response from AI")
    }

    private fun pdfToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                val maxPages = minOf(renderer.pageCount, 3)
                for (i in 0 until maxPages) {
                    val page = renderer.openPage(i)
                    // Render with a decent resolution (e.g., 2x of standard size for clear text)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    // Fill white background (since PDF is usually transparent)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
            }
        } finally {
            renderer?.close()
            pfd?.close()
        }
        return bitmaps
    }
}
