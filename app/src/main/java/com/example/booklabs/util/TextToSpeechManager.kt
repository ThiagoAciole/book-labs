package com.example.booklabs.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Gerenciador de Text-to-Speech para leitura de voz de livros
 * Suporta controle de reprodução, velocidade, pitch e navegação
 */
class TextToSpeechManager(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    // Estados observáveis
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()
    
    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()
    
    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()
    
    private var chapters: List<String> = emptyList()
    private var currentSentenceIndex = 0
    private var currentChapterSentences: List<String> = emptyList()
    
    // Callbacks
    var onChapterChange: ((Int) -> Unit)? = null
    var onSentenceChange: ((Int) -> Unit)? = null
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.forLanguageTag("pt-BR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Idioma PT-BR não suportado, usando padrão")
                    tts?.setLanguage(Locale.getDefault())
                }
                
                tts?.setSpeechRate(_speechRate.value)
                tts?.setPitch(_pitch.value)
                
                // Configurar listener de progresso
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("TTS", "Iniciou leitura: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d("TTS", "Finalizou leitura: $utteranceId")
                        // Avançar para próxima sentença
                        playNextSentence()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e("TTS", "Erro na leitura: $utteranceId")
                        _isPlaying.value = false
                    }
                })
                
                isInitialized = true
                Log.d("TTS", "TextToSpeech inicializado com sucesso")
            } else {
                Log.e("TTS", "Falha ao inicializar TextToSpeech")
            }
        }
    }
    
    /**
     * Define os capítulos a serem lidos
     */
    fun setChapters(chapterList: List<String>) {
        chapters = chapterList
        if (chapters.isNotEmpty()) {
            prepareChapter(0)
        }
    }
    
    /**
     * Prepara um capítulo para leitura, dividindo em sentenças
     */
    private fun prepareChapter(chapterIndex: Int) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size) return
        
        _currentChapterIndex.value = chapterIndex
        
        // Extrair texto puro do HTML
        val plainText = Html.fromHtml(chapters[chapterIndex], Html.FROM_HTML_MODE_COMPACT).toString()
        
        // Dividir em sentenças (por pontos, exclamações e interrogações)
        currentChapterSentences = plainText
            .split(Regex("[.!?]\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
        
        currentSentenceIndex = 0
        
        Log.d("TTS", "Capítulo $chapterIndex preparado com ${currentChapterSentences.size} sentenças")
    }
    
    /**
     * Inicia ou retoma a leitura
     */
    fun play() {
        if (!isInitialized) {
            Log.w("TTS", "TTS não inicializado ainda")
            return
        }
        
        if (chapters.isEmpty()) {
            Log.w("TTS", "Nenhum capítulo carregado")
            return
        }
        
        _isPlaying.value = true
        speakCurrentSentence()
    }
    
    /**
     * Pausa a leitura
     */
    fun pause() {
        tts?.stop()
        _isPlaying.value = false
    }
    
    /**
     * Para completamente a leitura
     */
    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        currentSentenceIndex = 0
        prepareChapter(_currentChapterIndex.value)
    }
    
    /**
     * Fala a sentença atual
     */
    private fun speakCurrentSentence() {
        if (currentSentenceIndex >= currentChapterSentences.size) {
            // Fim do capítulo, avançar para próximo
            playNextChapter()
            return
        }
        
        val sentence = currentChapterSentences[currentSentenceIndex]
        val utteranceId = "sentence_${_currentChapterIndex.value}_$currentSentenceIndex"
        
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        onSentenceChange?.invoke(currentSentenceIndex)
    }
    
    /**
     * Avança para próxima sentença
     */
    private fun playNextSentence() {
        if (!_isPlaying.value) return
        
        currentSentenceIndex++
        
        if (currentSentenceIndex >= currentChapterSentences.size) {
            playNextChapter()
        } else {
            speakCurrentSentence()
        }
    }
    
    /**
     * Avança para próximo capítulo
     */
    fun playNextChapter() {
        val nextChapter = _currentChapterIndex.value + 1
        if (nextChapter < chapters.size) {
            prepareChapter(nextChapter)
            onChapterChange?.invoke(nextChapter)
            if (_isPlaying.value) {
                speakCurrentSentence()
            }
        } else {
            // Fim do livro
            stop()
            Log.d("TTS", "Fim do livro alcançado")
        }
    }
    
    /**
     * Volta para capítulo anterior
     */
    fun playPreviousChapter() {
        val prevChapter = _currentChapterIndex.value - 1
        if (prevChapter >= 0) {
            prepareChapter(prevChapter)
            onChapterChange?.invoke(prevChapter)
            if (_isPlaying.value) {
                speakCurrentSentence()
            }
        }
    }
    
    /**
     * Vai para um capítulo específico
     */
    fun goToChapter(chapterIndex: Int) {
        if (chapterIndex >= 0 && chapterIndex < chapters.size) {
            val wasPlaying = _isPlaying.value
            pause()
            prepareChapter(chapterIndex)
            onChapterChange?.invoke(chapterIndex)
            if (wasPlaying) {
                play()
            }
        }
    }
    
    /**
     * Define a velocidade de leitura (0.5 a 2.0)
     */
    fun setSpeechRate(rate: Float) {
        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clampedRate
        tts?.setSpeechRate(clampedRate)
        Log.d("TTS", "Velocidade ajustada para: $clampedRate")
    }
    
    /**
     * Define o pitch da voz (0.5 a 2.0)
     */
    fun setPitch(pitchValue: Float) {
        val clampedPitch = pitchValue.coerceIn(0.5f, 2.0f)
        _pitch.value = clampedPitch
        tts?.setPitch(clampedPitch)
        Log.d("TTS", "Pitch ajustado para: $clampedPitch")
    }
    
    /**
     * Libera recursos do TTS
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isPlaying.value = false
    }
}
