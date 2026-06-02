package com.anonymous.csreader.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {
    private val ASSETS_TO_COPY = listOf(
        "reader.html",
        "pdf_viewer.html",
        "epub.min.js",
        "pdf.min.js",
        "pdf.worker.min.js"
    )

    fun copyAssetsToFilesDir(context: Context) {
        val filesDir = context.filesDir
        for (fileName in ASSETS_TO_COPY) {
            val targetFile = File(filesDir, fileName)
            try {
                context.assets.open(fileName).use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
