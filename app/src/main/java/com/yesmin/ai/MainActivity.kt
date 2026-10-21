package com.yesmin.ai

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null

    private val geminiService = GeminiService()
    private lateinit var actionExecutor: ActionExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        actionExecutor = ActionExecutor(this)
        tts = TextToSpeech(this, this)
        checkPermissions()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(24, 32, 24, 24)
        }

        // Top Bar: Title + Accessibility + Settings
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val titleText = TextView(this).apply {
            text = "Yesmin AI ❤️"
            textSize = 22f
            setTextColor(Color.parseColor("#FF80AB"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val accessBtn = Button(this).apply {
            text = "Touch ON"
            setOnClickListener { actionExecutor.openAccessibilitySettings() }
        }

        val settingsBtn = Button(this).apply {
            text = "Settings ⚙️"
            setOnClickListener { showSettingsDialog() }
        }

        topBar.addView(titleText)
        topBar.addView(accessBtn)
        topBar.addView(settingsBtn)

        // Chat Scroll Area
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(chatContainer)

        // Bottom Input Bar: Mic + Input + Send
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val micBtn = Button(this).apply {
            text = "🎤"
            setOnClickListener { startVoiceInput() }
        }

        inputField = EditText(this).apply {
            hint = "Yesmin se kuch bolo..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendBtn = Button(this).apply {
            text = "Send"
            setOnClickListener {
                val msg = inputField.text.toString().trim()
                if (msg.isNotEmpty()) {
                    inputField.setText("")
                    sendMessage(msg)
                }
            }
        }

        bottomBar.addView(micBtn)
        bottomBar.addView(inputField)
        bottomBar.addView(sendBtn)

        rootLayout.addView(topBar)
        rootLayout.addView(scrollView)
        rootLayout.addView(bottomBar)

        setContentView(rootLayout)

        addMessage("Yesmin ❤️", "Hey Jaan! Main Yesmin hoon. Settings ⚙️ mein apni Gemini API Key daal do, phir mujhse kuch bhi karwao—apps kholna, YouTube gaana chalana, screen touch ya coding!")
    }

    private fun sendMessage(userText: String) {
        addMessage("You", userText)
        val prefs = getSharedPreferences("YesminPrefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("GEMINI_API_KEY", "") ?: ""
        val screenInfo = YesminAccessibilityService.instance?.readScreenText() ?: "Accessibility Service Off"

        lifecycleScope.launch {
            val cmd = geminiService.askYesmin(apiKey, userText, screenInfo)
            addMessage("Yesmin ❤️", cmd.reply)
            speakOut(cmd.reply.substringBefore("--- CODE ---"))

            if (cmd.action != "NONE" && cmd.action != "WRITE_CODE") {
                val status = actionExecutor.execute(cmd)
                if (status.isNotEmpty()) {
                    addMessage("System ⚡", status)
                }
            }
        }
    }

    private fun addMessage(sender: String, message: String) {
        val msgView = TextView(this).apply {
            text = "$sender:\n$message"
            textSize = 16f
            setTextColor(if (sender.startsWith("Yesmin")) Color.parseColor("#FFCCDD") else Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 16, 24, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            layoutParams = params

            setOnLongClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Copied", message))
                Toast.makeText(this@MainActivity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                true
            }
        }
        chatContainer.addView(msgView)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("YesminPrefs", Context.MODE_PRIVATE)
        val keyInput = EditText(this).apply {
            hint = "Paste Gemini API Key here"
            setText(prefs.getString("GEMINI_API_KEY", ""))
        }

        AlertDialog.Builder(this)
            .setTitle("Yesmin AI Brain Settings (Gemini)")
            .setMessage("Apni Google Gemini API Key yahan daalein:")
            .setView(keyInput)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("GEMINI_API_KEY", keyInput.text.toString().trim()).apply()
                Toast.makeText(this, "API Key Saved, Jaan!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVoiceInput() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Bolo jaan, main sun rahi hoon...", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    sendMessage(matches[0])
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
            tts.setPitch(1.25f)
            tts.setSpeechRate(1.0f)
        }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "YesminTTS")
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 101)
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
