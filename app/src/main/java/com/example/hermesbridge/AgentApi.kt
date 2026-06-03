package com.example.hermesbridge

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

interface AgentApi {
    suspend fun sendMessage(url: String, request: AgentRequest): AgentResponse
}

class OkHttpAgentApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : AgentApi {

    override suspend fun sendMessage(url: String, request: AgentRequest): AgentResponse = withContext(Dispatchers.IO) {
        val targetUrl = AppConfig.API_BASE_URL.trimEnd('/') + "/" + AppConfig.AGENT_ENDPOINT.trimStart('/')
        Log.d("HermesBridge", "POST URL: $targetUrl")

        val requestBodyJson = gson.toJson(request)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBodyJson.toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url(targetUrl)
            .addHeader("X-API-KEY", AppConfig.API_KEY)
            .post(body)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AgentResponse(
                        error = "HTTP Error: ${response.code} - ${response.message}",
                        sessionId = request.sessionId
                    )
                }

                val responseBodyStr = response.body?.string()
                Log.d("HermesBridge", "RAW RESPONSE: $responseBodyStr")
                
                if (responseBodyStr.isNullOrEmpty()) {
                    return@withContext AgentResponse(
                        error = "Empty response body",
                        sessionId = request.sessionId
                    )
                }

                try {
                    val parsedResponse = gson.fromJson(responseBodyStr, AgentResponse::class.java)
                    parsedResponse ?: AgentResponse(
                        error = "Failed to parse JSON response",
                        sessionId = request.sessionId
                    )
                } catch (e: Exception) {
                    AgentResponse(
                        error = "Parse Error: ${e.message}",
                        sessionId = request.sessionId
                    )
                }
            }
        } catch (e: IOException) {
            AgentResponse(
                error = "Network Error: ${e.message}",
                sessionId = request.sessionId
            )
        }
    }
}
