package com.dogfit.logger

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Logger(private val context: Context) {
    private var writer: BufferedWriter? = null
    private var fileName: String? = null
    private var fileUri: Uri? = null

    fun startNewFile(): String {
        stop()
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        fileName = "imu_${formatter.format(Date())}.csv"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/DogfitLogs")
            }
        }

        val collection = MediaStore.Files.getContentUri("external")
        fileUri = context.contentResolver.insert(collection, contentValues)
        val outputStream = fileUri?.let { context.contentResolver.openOutputStream(it) }
        if (outputStream == null) {
            return fileName ?: "imu.csv"
        }
        writer = BufferedWriter(OutputStreamWriter(outputStream))
        writer?.write("ts_ms,ax,ay,az,gx,gy,gz\n")
        writer?.flush()
        return fileName ?: "imu.csv"
    }

    fun appendLine(line: String) {
        writer?.apply {
            write(line)
            write("\n")
            flush()
        }
    }

    fun stop() {
        writer?.flush()
        writer?.close()
        writer = null
        fileUri = null
    }

    fun isActive(): Boolean = writer != null
}
