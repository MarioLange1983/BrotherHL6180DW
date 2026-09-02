package dev.mariolange.brotherhl6180dw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.OutputStream

object PclConverter {

    private const val ESC = "\u001B"

    fun convertPdfToPcl(pfd: ParcelFileDescriptor, outputStream: OutputStream) {
        val renderer = PdfRenderer(pfd)
        
        // PCL Job Initialization
        outputStream.write("$ESC E".toByteArray()) // Reset
        outputStream.write("$ESC&l26A".toByteArray()) // A4 Paper
        outputStream.write("$ESC&l0O".toByteArray()) // Portrait Orientation
        
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            
            // Calculate dimensions for 300 DPI
            val width = (page.width * 300 / 72)
            val height = (page.height * 300 / 72)
            
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            
            // Convert to PCL Raster
            writeBitmapAsPcl(bitmap, outputStream)
            
            outputStream.write("\u000C".toByteArray()) // Form Feed
            
            page.close()
            bitmap.recycle()
        }
        
        outputStream.write("$ESC E".toByteArray()) // Final Reset
        renderer.close()
    }

    private fun writeBitmapAsPcl(bitmap: Bitmap, outputStream: OutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        
        outputStream.write("$ESC*t300R".toByteArray()) // Set Graphics Resolution
        outputStream.write("$ESC*r1A".toByteArray())  // Start Graphics at Left Margin
        
        val rowBytes = (width + 7) / 8
        val pixels = IntArray(width)
        val rowData = ByteArray(rowBytes)
        
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            rowData.fill(0)
            for (x in 0 until width) {
                val pixel = pixels[x]
                // Simple thresholding: (R+G+B)/3 < 128
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (r + g + b) / 3
                
                if (gray < 128) {
                    val byteIndex = x / 8
                    val bitIndex = 7 - (x % 8)
                    rowData[byteIndex] = (rowData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
            // Transfer Raster Data: ESC * b [length] W [data]
            outputStream.write("$ESC*b${rowBytes}W".toByteArray())
            outputStream.write(rowData)
        }
        
        outputStream.write("$ESC*rB".toByteArray()) // End Graphics
    }
}