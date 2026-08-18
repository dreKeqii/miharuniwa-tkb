package com.miharuniwa.tkb.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream

fun saveImageToStorage(context: Context, bitmap: Bitmap, folderUriStr: String?, fileName: String): Boolean {
    var outStream: OutputStream? = null
    try {
        if (!folderUriStr.isNullOrEmpty()) {
            val treeUri = Uri.parse(folderUriStr)
            val dir = DocumentFile.fromTreeUri(context, treeUri)
            val existing = dir?.findFile(fileName)
            val file = existing ?: dir?.createFile("image/png", fileName)
            if (file != null) {
                outStream = context.contentResolver.openOutputStream(file.uri)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/TKB")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) outStream = context.contentResolver.openOutputStream(uri)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "TKB")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                outStream = file.outputStream()
            }
        }
        
        var success = false
        outStream?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            success = true
        }
        return success
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}
