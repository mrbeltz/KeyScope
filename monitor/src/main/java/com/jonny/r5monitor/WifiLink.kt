package com.jonny.r5monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import com.jonny.r5monitor.ccapi.CcapiClient
import com.jonny.r5monitor.ccapi.CcapiParse
import com.jonny.r5monitor.ccapi.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Works out how to reach the camera. Two setups are common on a shoot:
 *
 * - The phone joins the camera's access point, or both join the same router. The camera is then
 *   on a Wi-Fi [Network], and sockets have to be bound to it (see [CcapiClient]).
 * - The camera joins the phone's hotspot. The hotspot is not a [Network] the app can see, but its
 *   subnet is directly attached, so an unbound socket reaches it.
 */
object WifiLink {

    data class Found(val address: String, val product: String)

    private data class Subnet(val base: Int, val self: Int, val network: Network?)

    /** The Wi-Fi network [host] sits on, or null to let the system route it (hotspot, or unknown). */
    fun networkFor(context: Context, host: String): Network? {
        val target = parseIpv4(host) ?: return null
        val wifi = wifiNetworks(context)
        for ((network, addresses) in wifi) {
            if (addresses.any { contains(it, target) }) return network
        }
        // A single Wi-Fi network and an address we cannot place: most likely the camera's AP
        // handed out an unusual range, so the Wi-Fi network is still the best bet.
        return wifi.singleOrNull()?.first
    }

    /**
     * Scans every /24 the phone has on Wi-Fi or its hotspot for something answering CCAPI. The
     * gateway goes first because on the camera's own access point the camera usually is the gateway.
     */
    suspend fun scan(context: Context, onProgress: (Float) -> Unit): List<Found> = withContext(Dispatchers.IO) {
        val subnets = localSubnets(context)
        val targets = LinkedHashMap<Int, Network?>()
        for (s in subnets) {
            targets.putIfAbsent(s.base or 1, s.network)
            for (last in 1..254) {
                val ip = s.base or last
                if (ip != s.self) targets.putIfAbsent(ip, s.network)
            }
        }
        if (targets.isEmpty()) return@withContext emptyList()

        val gate = Semaphore(48)
        var done = 0
        coroutineScope {
            targets.entries.map { (ip, network) ->
                async {
                    gate.withPermit {
                        val hit = probe(formatIpv4(ip), network)
                        synchronized(this@WifiLink) {
                            done++
                            onProgress(done.toFloat() / targets.size)
                        }
                        hit
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun probe(address: String, network: Network?): Found? {
        val open = try {
            Socket().use { socket ->
                network?.bindSocket(socket)
                socket.connect(InetSocketAddress(address, CcapiClient.DEFAULT_PORT), 350)
                true
            }
        } catch (e: Exception) {
            false
        }
        if (!open) return null
        return try {
            val info = CcapiParse.deviceInfo(CcapiClient(address, network).getJson(Paths.DEVICE_INFO))
            Found(address, info.product)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiNetworks(context: Context): List<Pair<Network, List<LinkAddress>>> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        return cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@mapNotNull null
            val props = cm.getLinkProperties(network) ?: return@mapNotNull null
            network to props.linkAddresses.filter { it.address is Inet4Address }
        }
    }

    private fun localSubnets(context: Context): List<Subnet> {
        val out = ArrayList<Subnet>()
        for ((network, addresses) in wifiNetworks(context)) {
            for (a in addresses) {
                val ip = toInt(a.address) ?: continue
                out += Subnet(ip and 0xFFFFFF00.toInt(), ip, network)
            }
        }
        // The hotspot interface. Only Wi-Fi style names, so a mobile data interface on a
        // carrier's private range never gets swept.
        runCatching {
            for (iface in NetworkInterface.getNetworkInterfaces()) {
                val name = iface.name.lowercase()
                if (!iface.isUp || !(name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("softap") || name.startsWith("wlan"))) continue
                for (addr in iface.inetAddresses) {
                    if (addr !is Inet4Address || !addr.isSiteLocalAddress) continue
                    val ip = toInt(addr) ?: continue
                    val base = ip and 0xFFFFFF00.toInt()
                    if (out.none { it.base == base }) out += Subnet(base, ip, null)
                }
            }
        }
        return out
    }

    private fun contains(link: LinkAddress, target: Int): Boolean {
        val ip = toInt(link.address) ?: return false
        val prefix = link.prefixLength.coerceIn(0, 32)
        if (prefix == 0) return true
        val mask = (-1 shl (32 - prefix))
        return (ip and mask) == (target and mask)
    }

    fun parseIpv4(text: String): Int? {
        val host = text.substringBefore(':').trim()
        val parts = host.split('.')
        if (parts.size != 4) return null
        var result = 0
        for (p in parts) {
            val v = p.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            result = (result shl 8) or v
        }
        return result
    }

    fun formatIpv4(ip: Int) = "${ip ushr 24 and 255}.${ip ushr 16 and 255}.${ip ushr 8 and 255}.${ip and 255}"

    private fun toInt(address: InetAddress): Int? {
        val b = (address as? Inet4Address)?.address ?: return null
        return (b[0].toInt() and 255 shl 24) or (b[1].toInt() and 255 shl 16) or
            (b[2].toInt() and 255 shl 8) or (b[3].toInt() and 255)
    }
}
