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
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class TcpClient(
    private val host: String,
    private val roomCode: String,
    private val listener: TcpConnectionListener
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val JOIN_TIMEOUT_MS = 10_000
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Any()
    @Volatile private var closing = false
    @Volatile private var connected = false
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    fun connect() {
        ioScope.launch {
            try {
                // 建连、握手和持续读取都固定在 IO 调度器中执行。
                val newSocket = Socket()
                socket = newSocket
                newSocket.connect(InetSocketAddress(host, TcpHost.PORT), CONNECT_TIMEOUT_MS)
                newSocket.soTimeout = JOIN_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8))
                writer = PrintWriter(OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true)
                sendDirect(NetworkMessage.Join(roomCode))
                val response = reader.readLine()?.let(NetworkMessageCodec::decode)
                    ?: throw SocketException("房主未返回验证结果")
                when (response) {
                    is NetworkMessage.JoinOk -> {
                        connected = true
                        newSocket.soTimeout = 0
                        listener.onConnected()
                        readMessages(reader)
                    }
                    is NetworkMessage.JoinError -> listener.onConnectionError(response.message)
                    else -> listener.onConnectionError("房主返回了无效的验证消息")
                }
            } catch (_: SocketTimeoutException) {
                if (!closing) listener.onConnectionError("连接超时，请检查房主 IP、Wi-Fi 和房间状态")
            } catch (_: ConnectException) {
                if (!closing) listener.onConnectionError("无法连接房主，请确认房主已创建房间且 IP 正确")
            } catch (e: Exception) {
                if (!closing) listener.onConnectionError("连接失败：${e.message ?: "请检查网络"}")
            } finally {
                if (!connected) closeResources()
            }
        }
    }

    private fun readMessages(reader: BufferedReader) {
        try {
            while (!closing) {
                val line = reader.readLine() ?: break
                val message = try {
                    NetworkMessageCodec.decode(line)
                } catch (e: Exception) {
                    listener.onMessage(NetworkMessage.Error(e.message ?: "协议错误"))
                    continue
                }
                listener.onMessage(message)
                if (message is NetworkMessage.Leave) break
            }
            if (!closing) listener.onDisconnected("房主已退出房间")
        } catch (e: Exception) {
            if (!closing) listener.onDisconnected(
                if (e is SocketException) "与房主的连接已断开，请检查 Wi-Fi" else "连接已断开：${e.message ?: "未知错误"}"
            )
        } finally {
            closeResources()
        }
    }

    fun send(message: NetworkMessage) {
        ioScope.launch {
            try {
                synchronized(writeLock) { if (connected) sendDirect(message) }
            } catch (e: Exception) {
                if (!closing) listener.onDisconnected("发送失败：${e.message ?: "请检查 Wi-Fi"}")
            }
        }
    }

    fun shutdown(sendLeave: Boolean) {
        if (closing) return
        closing = true
        ioScope.launch {
            if (sendLeave && connected) synchronized(writeLock) { sendDirect(NetworkMessage.Leave()) }
            closeResources()
            ioScope.cancel()
        }
    }

    private fun sendDirect(message: NetworkMessage) {
        val output = writer ?: throw SocketException("连接尚未建立")
        output.println(NetworkMessageCodec.encode(message))
        if (output.checkError()) throw SocketException("发送消息失败")
    }

    private fun closeResources() {
        connected = false
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
    }
}
