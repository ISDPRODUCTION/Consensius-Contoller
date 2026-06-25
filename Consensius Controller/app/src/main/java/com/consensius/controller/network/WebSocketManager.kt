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
import java.util.concurrent.atomic.AtomicLong

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

    // Track message sends to detect flooding (for debug logging only)
    private val lastSendTime = AtomicLong(0L)
    private var sendCount = 0

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

        // Shut down any existing client before creating a new one.
        try { client?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)      // no read timeout (streaming)
            .writeTimeout(10, TimeUnit.SECONDS)         // fail fast on stalled writes
            .connectTimeout(5, TimeUnit.SECONDS)
            // ← CRITICAL FIX: send a WebSocket ping every 20 s.
            // Without this, NAT gateways / firewalls silently drop idle TCP
            // connections, which OkHttp then reports as
            // "no close frame received or sent" (EOFException).
            .pingInterval(20, TimeUnit.SECONDS)
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
        // Guard: only send when connected.
        if (_connectionState.value !is ConnectionState.Connected) return false
        return try {
            webSocket?.send(message) ?: false
        } catch (e: Exception) {
            // OkHttp can throw if the socket is already closed.
            // Catch here so the FPS loop / touch handler never crash.
            Log.e(tag, "WebSocket send error: ${e.message}")
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
