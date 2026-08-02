package com.example.toctoe

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    private val ipv4Pattern = Regex(
        "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\." +
            "(25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)$"
    )

    fun isValidIpv4(value: String): Boolean = ipv4Pattern.matches(value.trim()) && value.trim() != "0.0.0.0"

    fun localIpv4Address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    .map { address -> network.name.lowercase() to address.hostAddress }
            }
            .sortedBy { (name, _) ->
                when {
                    "wlan" in name || "wifi" in name || "ap" in name -> 0
                    else -> 1
                }
            }
            .firstOrNull()?.second
    }.getOrNull()
}
