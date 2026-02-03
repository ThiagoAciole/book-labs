package com.example.booklabs.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object ContentLoader {
    
    data class EpubContent(val chapters: List<String>, val toc: List<ChapterInfo>)
    data class ChapterInfo(val title: String, val index: Int)

    sealed class LoaderResult {
        data class Pdf(val renderer: PdfRenderer) : LoaderResult()
        data class Images(val pagePaths: List<String>) : LoaderResult()
        data class Book(val chapters: List<String>, val toc: List<ChapterInfo> = emptyList()) : LoaderResult() // For EPUB/PDF
        data class Error(val message: String) : LoaderResult()
    }

    // ...

    enum class ContentType {
        PDF, EPUB, CBZ, CBR, UNKNOWN
    }

    private fun getType(fileName: String): ContentType {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".pdf") -> ContentType.PDF
            lower.endsWith(".epub") -> ContentType.EPUB
            lower.endsWith(".cbz") || lower.endsWith(".zip") -> ContentType.CBZ
            lower.endsWith(".cbr") || lower.endsWith(".rar") -> ContentType.CBR
            else -> ContentType.UNKNOWN
        }
    }

    private fun loadPdf(context: Context, file: File): LoaderResult {
        try {
            val chapters = mutableListOf<String>()
            
            // Initialize PDFBox-Android
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            
            // Load PDF document
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            
            try {
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                
                // Extract text from each page
                for (pageIndex in 1..document.numberOfPages) {
                    stripper.startPage = pageIndex
                    stripper.endPage = pageIndex
                    
                    val pageText = stripper.getText(document).trim()
                    
                    // Only add non-empty pages
                    if (pageText.isNotEmpty()) {
                        chapters.add(pageText)
                    }
                }
                
                document.close()
                
                if (chapters.isEmpty()) {
                    return LoaderResult.Error("PDF vazio ou não foi possível extrair conteúdo.")
                }
                
                return LoaderResult.Book(chapters)
            } catch (e: Exception) {
                document.close()
                throw e
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return LoaderResult.Error("Erro ao ler PDF: ${e.localizedMessage}")
        }
    }
    
    private fun loadEpub(file: File): LoaderResult {
        try {
            ZipFile(file).use { zip ->
                // ... (finding OPF path logic remains same) ...
                // 1. Find OPF file path
                val containerEntry = zip.getEntry("META-INF/container.xml")
                var opfPath: String? = null
                
                if (containerEntry != null) {
                    zip.getInputStream(containerEntry).use { input ->
                        val parser = Xml.newPullParser()
                        parser.setInput(input, null)
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                                opfPath = parser.getAttributeValue(null, "full-path")
                                break
                            }
                            eventType = parser.next()
                        }
                    }
                }
                
                // Fallback scan
                if (opfPath == null) {
                    opfPath = zip.entries().asSequence()
                        .firstOrNull { it.name.endsWith(".opf") }?.name
                }
                
                if (opfPath == null) return LoaderResult.Error("Arquivo OPF não encontrado no EPUB.")

                // 2. Parse OPF
                val opfEntry = zip.getEntry(opfPath!!) ?: return LoaderResult.Error("OPF não encontrado.")
                val opfDir = File(opfPath!!).parent?.let { "$it/" } ?: ""
                
                val manifest = mutableMapOf<String, String>()
                val spine = mutableListOf<String>()
                
                zip.getInputStream(opfEntry).use { input ->
                    val parser = Xml.newPullParser()
                    parser.setInput(input, null)
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "item" -> {
                                    val id = parser.getAttributeValue(null, "id")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (id != null && href != null) manifest[id] = href
                                }
                                "itemref" -> {
                                    val idref = parser.getAttributeValue(null, "idref")
                                    if (idref != null) spine.add(idref)
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }

                // 2.5 Find and Parse TOC
                val toc = mutableListOf<ChapterInfo>()
                val ncxEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".ncx") }
                if (ncxEntry != null) {
                    zip.getInputStream(ncxEntry).use { input ->
                        val parser = Xml.newPullParser()
                        parser.setInput(input, null)
                        var eventType = parser.eventType
                        var currentTitle = ""
                        var currentPlayOrder = -1
                        
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "navLabel" -> {
                                        // Skip to text tag
                                    }
                                    "text" -> {
                                        currentTitle = parser.nextText()
                                    }
                                    "content" -> {
                                        // We'll use the appearance order in spine for simplicity indexing
                                    }
                                }
                            } else if (eventType == XmlPullParser.END_TAG && parser.name == "navPoint") {
                                if (currentTitle.isNotEmpty()) {
                                    // Match title with index later or just use sequential order
                                    toc.add(ChapterInfo(currentTitle, toc.size))
                                    currentTitle = ""
                                }
                            }
                            eventType = parser.next()
                        }
                    }
                }
                
                // If TOC is empty, use default names
                // ...
                
                // 3. Extract text chapters
                val chapters = mutableListOf<String>()
                
                for (id in spine) {
                    val href = manifest[id] ?: continue
                    

                            
                    val targetPath = if (opfDir.isNotEmpty()) "$opfDir$href" else href
                    val entry = zip.getEntry(targetPath) ?: zip.getEntry(href)
                    
                    if (entry != null) {
                        zip.getInputStream(entry).use { input ->
                            val html = input.bufferedReader().use { it.readText() }
                            
                            // Extract content from body if present, else use whole text
                            var content = if (html.contains("<body", true)) {
                                html.substringAfter("<body")
                                    .substringAfter(">")
                                    .substringBefore("</body>")
                            } else {
                                html
                            }
                            
                            // Remove style, script, and image tags
                            content = content
                                .replace(Regex("(?si)<style[^>]*>.*?</style>"), "")
                                .replace(Regex("(?si)<script[^>]*>.*?</script>"), "")
                                .replace(Regex("(?si)<img[^>]*>"), "")
                                .replace(Regex("(?si)<image[^>]*>"), "")
                                .replace(Regex("(?si)<svg[^>]*>.*?</svg>"), "")
                                .replace(Regex("(?si)<!--.*?-->"), "")

                            // Improve list formatting (indentation and spacing)
                            // 1. Ensure list items don't contain paragraphs that cause line breaks
                            content = content.replace(Regex("(?si)<li>\\s*<p>"), "<li>")
                                           .replace(Regex("(?si)</p>\\s*</li>"), "</li>")
                            
                            // 2. Add indentation via nested structure
                            content = content
                                .replace(Regex("(?si)<ul[^>]*>"), "<br/><br/><ul><ul>") 
                                .replace(Regex("(?si)</ul>"), "</ul></ul><br/>")
                                .replace(Regex("(?si)<ol[^>]*>"), "<br/><br/><ol><ol>")
                                .replace(Regex("(?si)</ol>"), "</ol></ol><br/>")
                                
                                // Flatten list items: Remove block tags that force new lines
                                // Remove opening tags (p, div, h1-h6, br) and whitespace right after <li>
                                .replace(Regex("(?si)<li>\\s*(?:<(?:p|div|h[1-6]|br)[^>]*>|\\s+)+"), "<li>")
                                // Remove closing tags right before </li>
                                .replace(Regex("(?si)(?:</(?:p|div|h[1-6])>\\s*)+</li>"), "</li>")
                                
                                .replace(Regex("(?si)<li>"), "<li>&#160;&#160;&#160;") // Spacing
                            
                            // Normalize basic formatting tags for Android's Html.fromHtml
                            // (Mostly ensuring they don't have complex attributes that might break things)
                            
                            // Verifica conteúdo real (removendo tags HTML e espaços HTML)
                            val plainTextCheck = content
                                .replace(Regex("(?si)<[^>]*>"), "")
                                .replace("&nbsp;", "")
                                .replace("&#160;", "")
                                .trim()

                            if (plainTextCheck.isNotEmpty()) {
                                chapters.add(content.trim())
                            }
                        }
                    }
                }
                
                
                if (chapters.isEmpty()) return LoaderResult.Error("Não foi possível extrair texto do EPUB.")
                
                // Map TOC to indices if needed, or if TOC empty, generate basic one
                val finalToc = if (toc.isNotEmpty()) {
                    // Try to match toc entries with spine chapters if possible, 
                    // for now we'll assume 1:1 or use the first N chapters
                    toc.take(chapters.size)
                } else {
                    chapters.indices.map { ChapterInfo("Capítulo ${it + 1}", it) }
                }
                
                return LoaderResult.Book(chapters, finalToc)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return LoaderResult.Error("Erro ao ler EPUB: ${e.localizedMessage}")
        }
    }

    suspend fun loadContent(context: Context, file: File): LoaderResult {
        return withContext(Dispatchers.IO) {
            try {
                // ... setup cache ...
                val type = getType(file.name)
                // Use hash of path for cache to avoid collisions
                val cacheDir = File(context.cacheDir, "content_cache/${file.name.hashCode()}")
                
                // Check cache for images (CBZ/CBR)
                // ... same logic as before ...
                 if ((type == ContentType.CBZ || type == ContentType.CBR) && cacheDir.exists() && (cacheDir.listFiles()?.isNotEmpty() == true)) {
                     val images = cacheDir.listFiles()
                        ?.filter { isImage(it.name) }
                        ?.sortedBy { it.name } 
                        ?.map { it.absolutePath }
                    
                    if (!images.isNullOrEmpty()) {
                        return@withContext LoaderResult.Images(images)
                    }
                }
                
                when (type) {
                    ContentType.PDF -> loadPdf(context, file)
                    ContentType.CBZ -> loadCbz(file, cacheDir)
                    ContentType.CBR -> loadCbr(file, cacheDir)
                    ContentType.EPUB -> loadEpub(file)
                    ContentType.UNKNOWN -> {
                        LoaderResult.Error("Formato de arquivo desconhecido ou não suportado: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LoaderResult.Error("Erro ao carregar: ${e.message}")
            }
        }
    }
    
    // Adapted to use File instead of Uri for local files
    private fun loadCbz(file: File, cacheDir: File): LoaderResult {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val pages = mutableListOf<String>()
            
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && isImage(it.name) }
                    .sortedBy { it.name }
                    .toList()

                if (entries.isEmpty()) return LoaderResult.Error("Nenhuma imagem encontrada.")

                entries.forEachIndexed { index, entry ->
                    val extension = File(entry.name).extension
                    val safeName = "%04d.$extension".format(index)
                    val outFile = File(cacheDir, safeName)
                    
                    if (!outFile.exists()) {
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    pages.add(outFile.absolutePath)
                }
            }
            return LoaderResult.Images(pages)
        } catch (e: Exception) {
            return LoaderResult.Error("Erro CBZ: ${e.message}")
        }
    }

    private fun loadCbr(file: File, cacheDir: File): LoaderResult {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val pages = mutableListOf<String>()
            
            Archive(file).use { archive ->
                if (archive.isEncrypted) return LoaderResult.Error("CBR protegido não suportado.")
                
                val headers = archive.fileHeaders
                    .filter { !it.isDirectory && isImage(it.fileName) }
                    .sortedBy { it.fileName }
                
                if (headers.isEmpty()) return LoaderResult.Error("Nenhuma imagem encontrada.")

                headers.forEachIndexed { index, header ->
                     val extension = File(header.fileName).extension
                     val safeName = "%04d.$extension".format(index)
                     val outFile = File(cacheDir, safeName)
                     
                     if (!outFile.exists()) {
                         FileOutputStream(outFile).use { output ->
                             archive.extractFile(header, output)
                         }
                     }
                     pages.add(outFile.absolutePath)
                }
            }
            return LoaderResult.Images(pages)
        } catch (e: Exception) {
             val msg = e.message?.lowercase() ?: ""
             if (msg.contains("rar5") || msg.contains("bad header")) {
                 return LoaderResult.Error("Formato RAR5 não suportado.")
             }
            return LoaderResult.Error("Erro CBR: ${e.message}")
        }
    }

    suspend fun extractCover(context: Context, file: File, outputStream: java.io.OutputStream): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when (getType(file.name)) {
                    ContentType.CBZ -> extractCoverFromCbz(file, outputStream)
                    ContentType.CBR -> extractCoverFromCbr(file, outputStream)
                    ContentType.PDF -> extractCoverFromPdf(file, outputStream)
                     // EPUB extraction is tough without lib, skipping for now
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun extractCoverFromCbz(file: File, outputStream: java.io.OutputStream): Boolean {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.entries().asSequence()
                    .filter { !it.isDirectory && isImage(it.name) }
                    .minByOrNull { it.name } // First alphabetically
                
                if (entry != null) {
                    zip.getInputStream(entry).use { it.copyTo(outputStream) }
                    return true
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    private fun extractCoverFromCbr(file: File, outputStream: java.io.OutputStream): Boolean {
         try {
            Archive(file).use { archive ->
                if (!archive.isEncrypted) {
                    val header = archive.fileHeaders
                        .filter { !it.isDirectory && isImage(it.fileName) }
                        .minByOrNull { it.fileName }
                    
                    if (header != null) {
                        archive.extractFile(header, outputStream)
                        return true
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    private fun extractCoverFromPdf(file: File, outputStream: java.io.OutputStream): Boolean {
        try {
             android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    page.close()
                    renderer.close()
                    return true
                }
                renderer.close()
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    private fun isImage(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
    }
}
