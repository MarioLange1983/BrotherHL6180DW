package dev.mariolange.brotherhl6180dw

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    fun getWifiNetwork(context: Context): Network? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connectivityManager.allNetworks
        for (network in networks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return network
            }
        }
        return null
    }

    suspend fun fetchPrinterIppInfo(context: Context, ip: String, port: Int): PrinterIppInfo? = withContext(Dispatchers.IO) {
        try {
            val wifiNetwork = getWifiNetwork(context)
            val protocol = if (port == 443) "https" else "http"
            val url = URL("$protocol://$ip:$port/ipp")

            val connection = if (wifiNetwork != null) {
                wifiNetwork.openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
            } else {
                url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            }

            connection.requestMethod = "POST"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/ipp")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            connection.outputStream.use { os ->
                // IPP Get-Printer-Attributes
                os.write(byteArrayOf(0x01, 0x01, 0x00, 0x0b, 0x00, 0x00, 0x00, 0x01))
                os.write(0x01)
                writeIppAttribute(os, 0x47, "attributes-charset", "utf-8")
                writeIppAttribute(os, 0x48, "attributes-natural-language", "en")
                writeIppAttribute(os, 0x45, "printer-uri", "ipp://$ip/ipp")
                os.write(0x03)
            }

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { isStream ->
                    IppResponseParser.parsePrinterAttributes(isStream)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeIppAttribute(os: OutputStream, tag: Int, name: String, value: String) {
        os.write(tag)
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        os.write(ByteBuffer.allocate(2).putShort(nameBytes.size.toShort()).array())
        os.write(nameBytes)
        val valueBytes = value.toByteArray(StandardCharsets.UTF_8)
        os.write(ByteBuffer.allocate(2).putShort(valueBytes.size.toShort()).array())
        os.write(valueBytes)
    }

    suspend fun scanSubnet(context: Context, port: Int): List<String> = withContext(Dispatchers.IO) {
        val wifiNetwork = getWifiNetwork(context)
        val localIp = getLocalIpAddress(context) ?: return@withContext emptyList<String>()
        val subnet = localIp.substringBeforeLast(".")
        
        Log.d(TAG, "Scanning subnet $subnet.0/24 on port $port. Using WiFi Network: ${wifiNetwork != null}")

        // Try common printer ports if the provided one fails or scan multiple
        val portsToTry = listOf(port, 9100, 631, 515)
        
        (1..254).map { i ->
            async {
                val host = "$subnet.$i"
                val openPort = portsToTry.firstOrNull { port ->
                    if (port == 631 || port == 80 || port == 443) {
                        isIppPrinter(wifiNetwork, host, port)
                    } else {
                        isPortOpen(wifiNetwork, host, port)
                    }
                }
                if (openPort != null) host else null
            }
        }.awaitAll().filterNotNull()
    }

    private fun isIppPrinter(network: Network?, host: String, port: Int): Boolean {
        return try {
            val protocol = if (port == 443) "https" else "http"
            val url = URL("$protocol://$host:$port/ipp")
            
            val connection = if (network != null) {
                network.openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
            } else {
                url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            }

            connection.requestMethod = "POST"
            connection.connectTimeout = 300
            connection.readTimeout = 300
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/ipp")
            
            connection.outputStream.use { os ->
                // IPP Header: version 1.1, Get-Printer-Attributes
                os.write(byteArrayOf(0x01, 0x01, 0x00, 0x0b, 0x00, 0x00, 0x00, 0x01))
                os.write(0x01) // Operation Attributes
                os.write(0x03) // End
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun isPortOpen(network: Network?, host: String, port: Int): Boolean {
        return try {
            val socket = if (network != null) {
                val s = Socket(Proxy.NO_PROXY)
                network.bindSocket(s)
                s
            } else {
                Socket(Proxy.NO_PROXY)
            }
            socket.use { s ->
                s.connect(InetSocketAddress(host, port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = getWifiNetwork(context) ?: connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(wifiNetwork) ?: return null
        
        for (address in linkProperties.linkAddresses) {
            val inetAddress = address.address
            if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                return inetAddress.hostAddress
            }
        }
        return null
    }
}
