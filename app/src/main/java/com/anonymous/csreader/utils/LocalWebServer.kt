package com.anonymous.csreader.utils

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class LocalWebServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {

    init {
        Log.d("LocalWebServer", "Server started on port $port")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d("LocalWebServer", "Request URI: $uri")

        try {
            if (uri.startsWith("/assets/")) {
                val assetPath = uri.substringAfter("/assets/")
                val stream: InputStream = context.assets.open(assetPath)
                val mimeType = getCustomMimeTypeForFile(uri)
                return addCorsHeaders(newChunkedResponse(Response.Status.OK, mimeType, stream))
            } else if (uri.startsWith("/files/")) {
                val fileName = uri.substringAfter("/files/")
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val stream = FileInputStream(file)
                    val mimeType = getCustomMimeTypeForFile(uri)
                    return addCorsHeaders(newChunkedResponse(Response.Status.OK, mimeType, stream))
                } else {
                    return addCorsHeaders(newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found: $fileName"))
                }
            } else {
                return addCorsHeaders(newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<h1>CsReader Local Server Running</h1>"))
            }
        } catch (e: Exception) {
            Log.e("LocalWebServer", "Error serving request", e)
            return addCorsHeaders(newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error: ${e.message}"))
        }
    }

    private fun addCorsHeaders(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        return response
    }

    private fun getCustomMimeTypeForFile(uri: String): String {
        return when {
            uri.endsWith(".html") -> "text/html"
            uri.endsWith(".js") -> "application/javascript"
            uri.endsWith(".css") -> "text/css"
            uri.endsWith(".epub") -> "application/epub+zip"
            uri.endsWith(".pdf") -> "application/pdf"
            uri.endsWith(".png") -> "image/png"
            uri.endsWith(".jpg") || uri.endsWith(".jpeg") -> "image/jpeg"
            uri.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
