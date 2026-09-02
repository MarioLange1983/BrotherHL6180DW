package dev.mariolange.brotherhl6180dw

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.os.ParcelFileDescriptor
import android.util.Log
import android.net.ConnectivityManager
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class Hl6180dwPrintService : PrintService() {

    private val TAG = "Hl6180dwPrintService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: List<PrinterId>) {
                val sharedPrefs = getSharedPreferences("printer_settings", MODE_PRIVATE)
                val ip = sharedPrefs.getString("ip_address", "")
                if (ip.isNullOrBlank()) return

                val printerId = generatePrinterId("hl6180dw_printer")
                val printerInfo = PrinterInfo.Builder(
                    printerId,
                    getString(R.string.printer_name),
                    PrinterInfo.STATUS_IDLE
                )
                    .setDescription(getString(R.string.printer_description))
                    .setCapabilities(
                        PrinterCapabilitiesInfo.Builder(printerId)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                            .addResolution(PrintAttributes.Resolution("300dpi", "300 DPI", 300, 300), true)
                            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                            .build()
                    )
                    .build()

                addPrinters(listOf(printerInfo))
            }

            override fun onStopPrinterDiscovery() {}
            override fun onValidatePrinters(printerIds: List<PrinterId>) {}
            override fun onStartPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId) {}
            override fun onDestroy() {}
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        if (printJob.isQueued) {
            printJob.start()
            processPrintJob(printJob)
        }
    }

    private fun processPrintJob(printJob: PrintJob) {
        val sharedPrefs = getSharedPreferences("printer_settings", MODE_PRIVATE)
        val ip = (sharedPrefs.getString("ip_address", "") ?: "")
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
        val port = sharedPrefs.getInt("port", 80)
        Log.d(TAG, "Processing print job for IP: $ip, Port: $port")

        if (ip.isBlank()) {
            printJob.fail("Printer IP not configured")
            return
        }

        val document = printJob.document
        val pfd = document.data ?: run {
            printJob.fail("No data in print job")
            return
        }

        serviceScope.launch {
            try {
                if (port == 631 || port == 80 || port == 443) {
                    try {
                        sendViaIpp(ip, port, pfd, printJob)
                    } catch (e: Exception) {
                        Log.e(TAG, "IPP on $port failed, trying port 80 fallback", e)
                        if (port != 80) sendViaIpp(ip, 80, pfd, printJob) else throw e
                    }
                } else {
                    Log.d(TAG, "Sending job via RAW")
                    sendViaRaw(ip, port, pfd, printJob)
                }
                withContext(Dispatchers.Main) {
                    printJob.complete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Print job failed", e)
                withContext(Dispatchers.Main) {
                    printJob.fail(e.message ?: "Unknown error")
                }
            } finally {
                try { pfd.close() } catch (_: Exception) {}
            }
        }
    }

    private fun sendViaRaw(ip: String, port: Int, pfd: ParcelFileDescriptor, printJob: PrintJob) {
        val wifiNetwork = NetworkUtils.getWifiNetwork(this)
        Log.d(TAG, "Sending job via RAW to $ip:$port. WiFi Network: ${wifiNetwork != null}")
        
        val socket = if (wifiNetwork != null) {
            val s = Socket(Proxy.NO_PROXY)
            wifiNetwork.bindSocket(s)
            s
        } else {
            Socket(Proxy.NO_PROXY)
        }

        socket.use { s ->
            s.connect(InetSocketAddress(ip, port), 10000)
            s.getOutputStream().use { outputStream ->
                PclConverter.convertPdfToPcl(pfd, outputStream)
                outputStream.flush()
            }
        }
    }

    private fun sendViaIpp(ip: String, port: Int, pfd: ParcelFileDescriptor, printJob: PrintJob) {
        val protocol = if (port == 443) "https" else "http"
        val url = URL("$protocol://$ip:$port/ipp")
        val wifiNetwork = NetworkUtils.getWifiNetwork(this)
        Log.d(TAG, "Sending job via IPP to $url. WiFi Network: ${wifiNetwork != null}")
        
        val connection = if (wifiNetwork != null) {
            wifiNetwork.openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
        } else {
            url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
        }
        
        connection.requestMethod = "POST"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setChunkedStreamingMode(0) 
        connection.setRequestProperty("Content-Type", "application/ipp")
        connection.setRequestProperty("User-Agent", "Android/BrotherPrint")
        connection.setRequestProperty("Host", ip)
        
        connection.outputStream.use { os ->
            // IPP Header: version 1.1, operation Print-Job (0x0002), request-id 1
            os.write(byteArrayOf(0x01, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01))
            
            // Operation Attributes Tag (0x01)
            os.write(0x01)
            
            writeIppAttribute(os, 0x47, "attributes-charset", "utf-8")
            writeIppAttribute(os, 0x48, "attributes-natural-language", "en")
            writeIppAttribute(os, 0x45, "printer-uri", "ipp://$ip/ipp")
            writeIppAttribute(os, 0x42, "job-name", "Brother Print Job")
            
            // End of Attributes Tag (0x03)
            os.write(0x03)
            
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { inputStream ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
            }
        }
        
        val responseCode = connection.responseCode
        Log.d(TAG, "IPP Response Code: $responseCode")
        
        if (responseCode !in 200..299) {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e(TAG, "IPP Error Response: $errorMsg")
            throw Exception("IPP failed: $responseCode $errorMsg")
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}