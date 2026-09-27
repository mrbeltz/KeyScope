package com.jonny.r5monitor.ccapi

import android.net.Network
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A request the camera answered with an error, carrying its own explanation when it gave one. */
class CcapiException(val status: Int, message: String) : IOException(message)

/**
 * Thin HTTP client for Canon's Camera Control API.
 *
 * [network] matters more than it looks: a camera's own access point has no internet, so Android
 * keeps mobile data as the default network and an unbound socket to 192.168.x.x goes out over the
 * cellular modem and times out. Opening every connection on the Wi-Fi [Network] avoids that.
 */
class CcapiClient(address: String, private val network: Network?) {

    val host: String
    val port: Int

    init {
        val trimmed = address.trim().removePrefix("http://").trimEnd('/')
        val colon = trimmed.lastIndexOf(':')
        if (colon > 0 && trimmed.substring(colon + 1).toIntOrNull() != null) {
            host = trimmed.substring(0, colon)
            port = trimmed.substring(colon + 1).toInt()
        } else {
            host = trimmed
            port = DEFAULT_PORT
        }
    }

    @Volatile
    var endpoints: Endpoints = Endpoints.UNKNOWN
        private set

    fun open(path: String, method: String = "GET", readTimeoutMs: Int = 5_000): HttpURLConnection {
        val url = URL("http", host, port, path)
        val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = readTimeoutMs
        conn.useCaches = false
        return conn
    }

    /** Fetches the API list. Throws if nothing that looks like CCAPI is answering. */
    fun discover(): Endpoints {
        val body = String(request("GET", Paths.ROOT), Charsets.UTF_8)
        val parsed = try {
            Endpoints.parse(body)
        } catch (e: Exception) {
            throw IOException("Something answered on $host:$port, but it is not Canon CCAPI")
        }
        endpoints = parsed
        return parsed
    }

    fun getJson(path: String): JSONObject = JSONObject(String(request("GET", path), Charsets.UTF_8))

    fun getBytes(path: String): ByteArray = request("GET", path)

    fun put(path: String, body: JSONObject): JSONObject = json(request("PUT", path, body))

    fun post(path: String, body: JSONObject? = null): JSONObject = json(request("POST", path, body))

    fun delete(path: String) {
        request("DELETE", path)
    }

    private fun json(bytes: ByteArray): JSONObject {
        val text = String(bytes, Charsets.UTF_8).trim()
        return if (text.startsWith("{")) JSONObject(text) else JSONObject()
    }

    fun request(method: String, path: String, body: JSONObject? = null, readTimeoutMs: Int = 5_000): ByteArray {
        val conn = open(path, method, readTimeoutMs)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errorBody = conn.errorStream?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
                val reason = CcapiParse.errorMessage(errorBody) ?: conn.responseMessage ?: "HTTP $code"
                throw CcapiException(code, reason)
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}
