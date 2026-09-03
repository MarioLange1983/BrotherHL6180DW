package dev.mariolange.brotherhl6180dw

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class PrinterIppInfo(
    val makeAndModel: String = "",
    val printerName: String = "",
    val printerState: String = "",
    val stateCode: Int = 3,
    val tonerLevel: Int? = null,
    val tonerName: String = "",
    val acceptingJobs: Boolean = true,
    val queuedJobs: Int = 0,
    val speedPpm: Int = 40,
    val supportedResolutions: List<String> = emptyList(),
    val supportedTrays: List<String> = emptyList(),
    val supportedMediaTypes: List<String> = emptyList()
)

object IppResponseParser {

    fun parsePrinterAttributes(inputStream: InputStream): PrinterIppInfo {
        val dis = DataInputStream(inputStream)

        // Version (2 bytes)
        dis.readByte()
        dis.readByte()

        // Status code (2 bytes)
        val statusCode = dis.readUnsignedShort()
        if (statusCode != 0x0000) { // 0x0000 = successful-ok
            return PrinterIppInfo()
        }

        // Request ID (4 bytes)
        dis.readInt()

        var makeAndModel = ""
        var printerName = ""
        var stateCode = 3
        var tonerLevel: Int? = null
        var tonerName = ""
        var acceptingJobs = true
        var queuedJobs = 0
        var speedPpm = 40
        val resolutions = mutableListOf<String>()
        val trays = mutableListOf<String>()
        val mediaTypes = mutableListOf<String>()

        var currentLastName = ""

        try {
            while (dis.available() > 0) {
                val tag = dis.readUnsignedByte()
                if (tag == 0x03) { // End of attributes
                    break
                }
                if (tag == 0x01 || tag == 0x02 || tag == 0x04 || tag == 0x05) { // Delimiter tags
                    continue
                }

                // Name length
                val nameLen = dis.readUnsignedShort()
                val name = if (nameLen > 0) {
                    val nameBytes = ByteArray(nameLen)
                    dis.readFully(nameBytes)
                    String(nameBytes, StandardCharsets.UTF_8).also { currentLastName = it }
                } else {
                    currentLastName
                }

                // Value length
                val valLen = dis.readUnsignedShort()
                val valBytes = ByteArray(valLen)
                dis.readFully(valBytes)

                when (name) {
                    "printer-make-and-model" -> {
                        makeAndModel = String(valBytes, StandardCharsets.UTF_8)
                    }
                    "printer-name" -> {
                        printerName = String(valBytes, StandardCharsets.UTF_8)
                    }
                    "printer-state" -> {
                        if (valLen == 4) {
                            stateCode = ByteBuffer.wrap(valBytes).int
                        }
                    }
                    "marker-levels" -> {
                        if (valLen == 4) {
                            tonerLevel = ByteBuffer.wrap(valBytes).int
                        }
                    }
                    "marker-names" -> {
                        tonerName = String(valBytes, StandardCharsets.UTF_8)
                    }
                    "printer-is-accepting-jobs" -> {
                        if (valLen == 1) {
                            acceptingJobs = valBytes[0].toInt() != 0
                        }
                    }
                    "queued-job-count" -> {
                        if (valLen == 4) {
                            queuedJobs = ByteBuffer.wrap(valBytes).int
                        }
                    }
                    "pages-per-minute" -> {
                        if (valLen == 4) {
                            speedPpm = ByteBuffer.wrap(valBytes).int
                        }
                    }
                    "printer-resolution-supported" -> {
                        if (valLen == 9) {
                            val buffer = ByteBuffer.wrap(valBytes)
                            val xres = buffer.int
                            buffer.int // yres
                            resolutions.add("${xres}dpi")
                        }
                    }
                    "media-source-supported" -> {
                        trays.add(String(valBytes, StandardCharsets.UTF_8))
                    }
                    "media-type-supported" -> {
                        mediaTypes.add(String(valBytes, StandardCharsets.UTF_8))
                    }
                }
            }
        } catch (_: Exception) {
            // End of stream or parse error
        }

        val stateText = when (stateCode) {
            3 -> "Bereit"
            4 -> "Druckt"
            5 -> "Gestoppt"
            else -> "Unbekannt"
        }

        return PrinterIppInfo(
            makeAndModel = makeAndModel.ifBlank { "Brother HL-6180DW series" },
            printerName = printerName,
            printerState = stateText,
            stateCode = stateCode,
            tonerLevel = tonerLevel,
            tonerName = tonerName.ifBlank { "Black Toner Cartridge" },
            acceptingJobs = acceptingJobs,
            queuedJobs = queuedJobs,
            speedPpm = speedPpm,
            supportedResolutions = resolutions,
            supportedTrays = trays,
            supportedMediaTypes = mediaTypes
        )
    }
}
