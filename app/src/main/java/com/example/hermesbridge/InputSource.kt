package com.example.hermesbridge

import kotlinx.coroutines.flow.SharedFlow

interface InputSource {
    val name: String
    val type: String
    
    // Emissions of raw text inputs entering the bridge
    val textInputs: SharedFlow<String>
    
    // Triggers submission of input path
    suspend fun sendInput(text: String)
}
