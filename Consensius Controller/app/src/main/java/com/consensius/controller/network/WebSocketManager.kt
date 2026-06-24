package com.consensius.controller.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class WebSocketManager(private val coroutineScope: CoroutineScope) {

    private val tag = "WebSocketManager"
    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var currentIp: String = ""
    private var currentPort: Int = 0
    private var autoReconnect = true
    private var reconnectJob: Job? = null

    fun connect(ip: String, port: Int) {
        currentIp = ip
        currentPort = port
        autoReconnect = true
        reconnectJob?.cancel()

        _connectionState.value = ConnectionState.Connecting

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://$ip:$port")
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket Connected")
                _connectionState.value = ConnectionState.Connected
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(tag, "Received message: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closing: $code / $reason")
                _connectionState.value = ConnectionState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(tag, "WebSocket closed: $code / $reason")
                _connectionState.value = ConnectionState.Disconnected
                if (autoReconnect) {
                    triggerReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
                if (autoReconnect) {
                    triggerReconnect()
                }
            }
        })
    }

    fun disconnect() {
        autoReconnect = false
        reconnectJob?.cancel()
        try {
            webSocket?.close(1000, "Goodbye!")
        } catch (e: Exception) {
            Log.e(tag, "Error closing websocket", e)
        }
        webSocket = null
        client = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun send(message: String): Boolean {
        return if (_connectionState.value is ConnectionState.Connected) {
            webSocket?.send(message) ?: false
        } else {
            false
        }
    }

    private fun triggerReconnect() {
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch(Dispatchers.IO) {
            delay(3000)
            if (autoReconnect && _connectionState.value !is ConnectionState.Connected) {
                Log.d(tag, "Attempting auto-reconnect...")
                connect(currentIp, currentPort)
            }
        }
    }
}
