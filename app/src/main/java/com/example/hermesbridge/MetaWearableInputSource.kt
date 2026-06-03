package com.example.hermesbridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * MetaWearableInputSource implements the same InputSource interface.
 * When integrated, this adapter connects to the wearable SDK (e.g., Ray-Ban Meta sensor telemetry or
 * offline transcripts) and emits texts into the pipeline.
 *
 * This clean architecture makes it a direct drop-in replacement for the phone keyboard.
 */
class MetaWearableInputSource : InputSource {
    override val name: String = "Ray-Ban Meta Wearable"
    override val type: String = "wearable_meta"

    private val _textInputs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val textInputs: SharedFlow<String> = _textInputs.asSharedFlow()

    override suspend fun sendInput(text: String) {
        if (text.isNotBlank()) {
            val formattedText = "[Wearable State Capture] $text"
            _textInputs.emit(formattedText)
        }
    }
}
