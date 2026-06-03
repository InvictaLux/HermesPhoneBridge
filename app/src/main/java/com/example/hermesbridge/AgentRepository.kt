package com.example.hermesbridge

class AgentRepository(private val api: AgentApi) {
    suspend fun sendMessage(url: String, request: AgentRequest): AgentResponse {
        return api.sendMessage(url, request)
    }
}
