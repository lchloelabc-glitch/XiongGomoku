package com.example.toctoe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class TcpHost(
    private val roomCode: String,
    private val listener: TcpConnectionListener
) {
    companion object {
        const val PORT = 8988
        private const val JOIN_TIMEOUT_MS = 10_000
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()
    @Volatile private var closing = false
    @Volatile private var joined = false
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    fun start() {
        ioScope.launch {
            try {
                // 只在 IO 线程绑定和等待连接，避免阻塞 Compose 主线程。
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT), 1)
                }
                while (!closing && !joined) acceptAndValidate()
            } catch (_: BindException) {
                if (!closing) listener.onConnectionError("端口 $PORT 已被占用，请关闭其他房间后重试")
            } catch (e: Exception) {
                if (!closing) listener.onConnectionError(networkError("创建房间失败", e))
            } finally {
                if (!joined) closeResources()
            }
        }
    }

    private fun acceptAndValidate() {
        val accepted = serverSocket?.accept() ?: return
        try {
            accepted.soTimeout = JOIN_TIMEOUT_MS
            val localReader = BufferedReader(InputStreamReader(accepted.getInputStream(), StandardCharsets.UTF_8))
            val localWriter = PrintWriter(OutputStreamWriter(accepted.getOutputStream(), StandardCharsets.UTF_8), true)
            val firstLine = localReader.readLine() ?: throw IllegalArgumentException("未收到加入请求")
            val firstMessage = NetworkMessageCodec.decode(firstLine)
            if (firstMessage !is NetworkMessage.Join) {
                sendDirect(localWriter, NetworkMessage.JoinError("首条消息必须是 JOIN"))
                return
            }
            if (firstMessage.roomCode != roomCode) {
                sendDirect(localWriter, NetworkMessage.JoinError("房间号错误"))
                return
            }

            socket = accepted
            writer = localWriter
            joined = true
            accepted.soTimeout = 0
            sendDirect(localWriter, NetworkMessage.JoinOk())
            serverSocket?.close()
            serverSocket = null
            listener.onConnected()
            readMessages(localReader)
        } catch (e: SocketTimeoutException) {
            // 未及时发送房间号的连接会被丢弃，房主继续等待下一位玩家。
        } catch (e: Exception) {
            if (joined && !closing) listener.onDisconnected(networkError("连接已断开", e))
        } finally {
            if (!joined) runCatching { accepted.close() }
        }
    }

    private fun readMessages(reader: BufferedReader) {
        try {
            while (!closing) {
                val line = reader.readLine() ?: break
                val message = try {
                    NetworkMessageCodec.decode(line)
                } catch (e: Exception) {
                    send(NetworkMessage.Error(e.message ?: "协议错误"))
                    continue
                }
                listener.onMessage(message)
                if (message is NetworkMessage.Leave) break
            }
            if (!closing) listener.onDisconnected("对方已退出房间")
        } catch (e: Exception) {
            if (!closing) listener.onDisconnected(networkError("对方网络连接已断开", e))
        } finally {
            closeResources()
        }
    }

    fun send(message: NetworkMessage) {
        ioScope.launch {
            try {
                synchronized(writeLock) { writer?.let { sendDirect(it, message) } }
            } catch (e: Exception) {
                if (!closing) listener.onDisconnected(networkError("发送失败", e))
            }
        }
    }

    fun shutdown(sendLeave: Boolean) {
        if (closing) return
        closing = true
        ioScope.launch {
            if (sendLeave && joined) synchronized(writeLock) {
                writer?.let { sendDirect(it, NetworkMessage.Leave()) }
            }
            closeResources()
            ioScope.cancel()
        }
    }

    private fun sendDirect(output: PrintWriter, message: NetworkMessage) {
        output.println(NetworkMessageCodec.encode(message))
        if (output.checkError()) throw SocketException("发送消息失败")
    }

    private fun closeResources() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        writer = null
        socket = null
        serverSocket = null
    }

    private fun networkError(prefix: String, error: Exception): String =
        if (error is SocketException) "$prefix，请检查 Wi-Fi 连接" else "$prefix：${error.message ?: "未知错误"}"
}
