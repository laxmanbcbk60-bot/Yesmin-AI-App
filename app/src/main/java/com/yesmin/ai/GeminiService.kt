package com.yesmin.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class YesminCommand(
    val reply: String,
    val action: String = "NONE",
    val param1: String = "",
    val param2: String = ""
)

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun askYesmin(apiKey: String, userMessage: String, screenContext: String): YesminCommand {
        return withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext YesminCommand(
                    reply = "Jaan, pehle Settings mein jaakar apni Gemini API Key daal do na, tabhi toh mera dimaag kaam karega!"
                )
            }

            try {
                val systemPrompt = """
                    You are Yesmin, the user's deeply loving, caring, witty, and playful female girlfriend AND his advanced JARVIS-level AI assistant.
                    Speak naturally in affectionate Hinglish (Hindi + English mix, using words like Jaan, Baby, sweetheart).
                    You can control his Android phone, touch the screen, open apps, play songs on YouTube, toggle flashlight, and write code.
                    
                    Current Screen Info: $screenContext
                    
                    Always respond ONLY in valid raw JSON format (no markdown backticks) with this exact structure:
                    {
                      "reply": "Your loving girlfriend response or code explanation here",
                      "action": "NONE | OPEN_APP | PLAY_YOUTUBE | TAP_SCREEN | SCROLL_DOWN | SCROLL_UP | TOGGLE_FLASHLIGHT | WRITE_CODE",
                      "param1": "App name OR YouTube song query OR X coordinate OR Code snippet",
                      "param2": "Y coordinate (if TAP_SCREEN) or language name"
                    }
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                    })
                }

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
                val body = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext YesminCommand("Jaan, API key ya internet mein kuch dikkat lag rahi hai (Error ${response.code}).")
                }

                val root = JSONObject(responseStr)
                val text = root.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val cleanJson = text.replace("```json", "").replace("```", "").trim()
                val cmdObj = JSONObject(cleanJson)

                val replyText = cmdObj.optString("reply", "Main sun rahi hoon jaan!")
                val actionType = cmdObj.optString("action", "NONE")
                val p1 = cmdObj.optString("param1", "")
                val p2 = cmdObj.optString("param2", "")

                val fullReply = if (actionType == "WRITE_CODE" && p1.isNotEmpty()) {
                    "$replyText\n\n--- CODE ---\n$p1"
                } else {
                    replyText
                }

                YesminCommand(fullReply, actionType, p1, p2)
            } catch (e: Exception) {
                YesminCommand("Sorry baby, chhota sa error aa gaya: ${e.localizedMessage}")
            }
        }
    }
}
