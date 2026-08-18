package com.miharuniwa.tkb.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PdfDownloader(private val context: Context, private val client: OkHttpClient) {

    suspend fun downloadPdf(fileId: String, fileName: String): File? {
        return withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, fileName)
            if (file.exists() && file.length() > 0) return@withContext file

            val url = "https://drive.google.com/uc?export=download&id=$fileId"
            val request = Request.Builder().url(url).build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val inputStream: InputStream = response.body!!.byteStream()
                    val outputStream = FileOutputStream(file)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    fun getLocalFile(fileName: String): File? {
        val file = File(context.cacheDir, fileName)
        return if (file.exists() && file.length() > 0) file else null
    }
}
