package com.example.toctoe

interface TcpConnectionListener {
    fun onConnected()
    fun onMessage(message: NetworkMessage)
    fun onConnectionError(message: String)
    fun onDisconnected(message: String)
}
