package com.example.hermesbridge

interface ResponseOutput {
    val name: String
    
    // Outputs the response text (e.g., speaks it or directs it to the engine)
    fun outputResponse(text: String, onComplete: () -> Unit = {})
    
    // Stops current output streams immediately
    fun stop()
    
    // Releases resources (like TTS binding) when done
    fun release()
}
