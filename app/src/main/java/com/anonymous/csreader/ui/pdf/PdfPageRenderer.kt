package com.anonymous.csreader.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfPageRenderer(
    private val context: Context,
    private val filePath: String
) {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    // Cache up to 24 rendered pages in memory for instant transitions
    private val cacheSize = 24
    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSize) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Memory is managed by GC
        }
    }

    var pageCount: Int = 0
        private set

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = File(filePath)
                val pfd = if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    val uri = Uri.parse(filePath)
                    context.contentResolver.openFileDescriptor(uri, "r")
                }

                if (pfd != null) {
                    fileDescriptor = pfd
                    renderer = PdfRenderer(pfd)
                    pageCount = renderer?.pageCount ?: 0
                    return@withLock true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withLock false
        }
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        bitmapCache.get(pageIndex)?.let { return@withContext it }

        mutex.withLock {
            // Check cache again inside lock
            bitmapCache.get(pageIndex)?.let { return@withLock it }

            val r = renderer ?: return@withLock null
            var page: PdfRenderer.Page? = null
            try {
                page = r.openPage(pageIndex)
                val origWidth = page.width
                val origHeight = page.height

                // Calculate display scale (sharp rendering, min 1.5x)
                val scale = if (targetWidth > 0 && targetHeight > 0) {
                    val scaleX = targetWidth.toFloat() / origWidth
                    val scaleY = targetHeight.toFloat() / origHeight
                    maxOf(scaleX, scaleY).coerceIn(1.2f, 2.5f)
                } else {
                    1.5f
                }

                val renderWidth = (origWidth * scale).toInt()
                val renderHeight = (origHeight * scale).toInt()

                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                // White background for transparent PDF pages
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmapCache.put(pageIndex, bitmap)
                return@withLock bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                return@withLock null
            } finally {
                try {
                    page?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getCachedBitmap(pageIndex: Int): Bitmap? {
        return bitmapCache.get(pageIndex)
    }

    fun close() {
        try {
            renderer?.close()
            renderer = null
            fileDescriptor?.close()
            fileDescriptor = null
            bitmapCache.evictAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
