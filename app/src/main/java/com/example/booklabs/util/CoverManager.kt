package com.example.booklabs.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object CoverManager {

    fun getCoverFile(comicFile: File): File {
        val parent = comicFile.parentFile
        val coversDir = File(parent, "Covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        return File(coversDir, "${comicFile.nameWithoutExtension}.jpg")
    }

    fun hasCover(comicFile: File): Boolean {
        return getCoverFile(comicFile).exists()
    }

    fun saveCover(context: Context, comicFile: File, sourceUri: Uri) {
        try {
            val destFile = getCoverFile(comicFile)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Tries to extract cover from file types. Returns true if successful.
    fun extractAndSaveCover(context: Context, comicFile: File): Boolean {
        val destFile = getCoverFile(comicFile)
        if (destFile.exists()) return true

        try {
            val name = comicFile.name.lowercase()
            when {
                name.endsWith(".pdf") -> return extractPdfCover(comicFile, destFile)
                name.endsWith(".cbz") || name.endsWith(".zip") || name.endsWith(".epub") -> return extractZipCover(comicFile, destFile)
                // CBR/RAR requires native libraries (Junrar/Unrar), skipping for now standard implementation
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun extractPdfCover(pdfFile: File, destFile: File): Boolean {
        try {
            val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                // Define resolution (e.g., width 600px, maintain aspect ratio)
                val width = 600
                val height = (width.toFloat() / page.width * page.height).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                fd.close()

                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                return true
            }
            renderer.close()
            fd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun extractZipCover(zipFile: File, destFile: File): Boolean {
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                // Simple heuristic: find first image file alphabetically or logic based on standard naming
                // Often covers are 000.jpg, cover.jpg, or just the first one.
                val imageEntry = entries.sortedBy { it.name }.firstOrNull { entry ->
                    val n = entry.name.lowercase()
                    !entry.isDirectory && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                }

                if (imageEntry != null) {
                    zip.getInputStream(imageEntry).use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                        FileOutputStream(destFile).use { out ->
                            // Always save as JPG for consistency
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
