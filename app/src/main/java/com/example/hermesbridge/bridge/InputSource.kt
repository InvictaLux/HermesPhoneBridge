package com.example.hermesbridge.bridge

interface InputSource {
    fun start()
    fun stop()
    fun setListener(listener: (BridgeEvent) -> Unit)
}
