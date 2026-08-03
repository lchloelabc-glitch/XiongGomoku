package com.example.toctoe

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface WebSocketGameListener {
    fun onSocketOpen()
    fun onMessage(message: ServerMessage)
    fun onConnectionError(message: String)
    fun onDisconnected(message: String)
}

class WebSocketGameClient(
    private val listener: WebSocketGameListener
) {
    companion object {
        // 全应用共享连接池和线程池，避免每次进房都创建新的网络资源。
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var manuallyClosed = false
    private var webSocket: WebSocket? = null

    fun connect() {
        if (webSocket != null) return
        manuallyClosed = false
        val request = Request.Builder().url(ServerConfig.WEB_SOCKET_URL).build()
        webSocket = httpClient.newWebSocket(request, socketListener)
    }

    fun send(text: String): Boolean = webSocket?.send(text) == true

    fun close(sendLeave: Boolean) {
        if (manuallyClosed) return
        manuallyClosed = true
        val socket = webSocket
        if (sendLeave) socket?.send(NetworkMessageCodec.playerLeave())
        socket?.close(1000, "用户退出房间")
        webSocket = null
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener.onSocketOpen()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                listener.onMessage(NetworkMessageCodec.decodeServerMessage(text))
            } catch (error: Exception) {
                listener.onConnectionError(error.message ?: "服务器消息解析失败")
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            listener.onConnectionError("服务器返回了不支持的二进制消息")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@WebSocketGameClient.webSocket = null
            if (!manuallyClosed) {
                listener.onDisconnected(if (reason.isBlank()) "服务器连接已关闭" else reason)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            this@WebSocketGameClient.webSocket = null
            if (manuallyClosed) return
            val message = when (t) {
                is SocketTimeoutException -> "连接服务器超时，请检查网络"
                is ConnectException -> "无法连接公网服务器，请稍后重试"
                else -> "服务器连接失败：${t.message ?: "请检查网络"}"
            }
            listener.onConnectionError(message)
        }
    }
}
