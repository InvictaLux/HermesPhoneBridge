package com.example.hermesbridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PhoneTextInputSource : InputSource {
    override val name: String = "On-Screen Keyboard"
    override val type: String = "phone_text"

    private val _textInputs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val textInputs: SharedFlow<String> = _textInputs.asSharedFlow()

    override suspend fun sendInput(text: String) {
        if (text.isNotBlank()) {
            _textInputs.emit(text.trim())
        }
    }
}
