package com.example.lmsnowplaying.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

// A server found on the network.
data class FoundServer(val name: String, val url: String)

// Finds Lyrion / Logitech Media Server on the local network (same network only):
// a UDP broadcast on port 3483 that every server answers with its name and web port.
object ServerDiscovery {
    // "e" + IPAD, then the tags NAME and JSON, each with length 0 = "tell me this"
    private val query = byteArrayOf(
        'e'.code.toByte(), 'I'.code.toByte(), 'P'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), 0,
        'N'.code.toByte(), 'A'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte(), 0,
        'J'.code.toByte(), 'S'.code.toByte(), 'O'.code.toByte(), 'N'.code.toByte(), 0,
    )

    // Jellyfin answers the text "who is JellyfinServer?" on UDP port 7359 with JSON: Address, Id, Name
    suspend fun findJellyfin(context: Context, waitMs: Long = 2000): List<FoundServer> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, FoundServer>()
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("lmsnp-discovery-jf")?.apply { setReferenceCounted(false); acquire() }
        try {
            val message = "who is JellyfinServer?".toByteArray()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                broadcastTargets().forEach { try { socket.send(DatagramPacket(message, message.size, it, 7359)) } catch (_: Exception) { } }
                val end = System.currentTimeMillis() + waitMs
                while (System.currentTimeMillis() < end) {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    try {
                        val json = org.json.JSONObject(String(buffer, 0, packet.length, Charsets.UTF_8))
                        val address = json.optString("Address", "").trimEnd('/')
                        if (address.isNotEmpty()) found[address] = FoundServer(json.optString("Name", "").ifBlank { "Jellyfin" }, address)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (lock != null && lock.isHeld) lock.release()
        }
        found.values.toList()
    }

    // The addresses a broadcast goes to: the general one and the one of every network the device is on
    private fun broadcastTargets(): List<InetAddress> {
        val targets = ArrayList<InetAddress>()
        targets.add(InetAddress.getByName("255.255.255.255"))
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .forEach { net ->
                net.interfaceAddresses.forEach { a ->
                    if (a.address is Inet4Address && a.broadcast != null) targets.add(a.broadcast)
                }
            }
        return targets
    }

    suspend fun findLyrion(context: Context, waitMs: Long = 2000): List<FoundServer> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, FoundServer>()
        // Without this lock many devices drop broadcast answers
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("lmsnp-discovery")?.apply { setReferenceCounted(false); acquire() }
        try {
            val targets = broadcastTargets()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                targets.forEach { try { socket.send(DatagramPacket(query, query.size, it, 3483)) } catch (_: Exception) { } }
                val end = System.currentTimeMillis() + waitMs
                while (System.currentTimeMillis() < end) {
                    val buffer = ByteArray(512)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    if (packet.length < 2 || buffer[0] != 'E'.code.toByte()) continue
                    // After the E: tag (4 letters), length (1 byte), value
                    var name = ""
                    var port = 9000
                    var pos = 1
                    while (pos + 5 <= packet.length) {
                        val tag = String(buffer, pos, 4, Charsets.US_ASCII)
                        pos += 4
                        val len = buffer[pos++].toInt() and 0xff
                        if (pos + len > packet.length) break
                        val value = String(buffer, pos, len, Charsets.UTF_8)
                        if (tag == "NAME") name = value
                        if (tag == "JSON") port = value.toIntOrNull()?.takeIf { it in 1..65535 } ?: 9000
                        pos += len
                    }
                    val ip = packet.address.hostAddress ?: continue
                    found["$ip:$port"] = FoundServer(name.ifBlank { "Lyrion" }, "http://$ip:$port")
                }
            }
        } catch (_: Exception) {
        } finally {
            if (lock != null && lock.isHeld) lock.release()
        }
        found.values.toList()
    }
}
