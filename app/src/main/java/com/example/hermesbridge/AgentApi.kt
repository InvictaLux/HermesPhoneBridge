package com.example.hermesbridge

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
        val requestBodyJson = gson.toJson(request)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBodyJson.toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AgentResponse(
                        ok = false,
                        responseText = null,
                        sessionId = request.sessionId,
                        error = "HTTP Error: ${response.code} - ${response.message}"
                    )
                }

                val responseBodyStr = response.body?.string()
                if (responseBodyStr.isNullOrEmpty()) {
                    return@withContext AgentResponse(
                        ok = false,
                        responseText = null,
                        sessionId = request.sessionId,
                        error = "Empty response body"
                    )
                }

                try {
                    val parsedResponse = gson.fromJson(responseBodyStr, AgentResponse::class.java)
                    parsedResponse ?: AgentResponse(
                        ok = false,
                        responseText = null,
                        sessionId = request.sessionId,
                        error = "Failed to parse JSON response"
                    )
                } catch (e: Exception) {
                    AgentResponse(
                        ok = false,
                        responseText = null,
                        sessionId = request.sessionId,
                        error = "Parse Error: ${e.message}"
                    )
                }
            }
        } catch (e: IOException) {
            AgentResponse(
                ok = false,
                responseText = null,
                sessionId = request.sessionId,
                error = "Network Error: ${e.message}"
            )
        }
    }
}
