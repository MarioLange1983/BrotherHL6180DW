package dev.mariolange.brotherhl6180dw

import android.Manifest
import android.content.Intent
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class SettingsActivity : ComponentActivity() {

    private val TAG = "SettingsActivity"
    private val sharedPrefs by lazy { getSharedPreferences("printer_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        requestPermissions(permissions.toTypedArray(), 100)

        setContent {
            val colorScheme = darkColorScheme(
                primary = Color(0xFF2C3E50),
                secondary = Color(0xFF34495E),
                tertiary = Color(0xFF1ABC9C),
                background = Color(0xFF17202A),
                surface = Color(0xFF1C2833),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color(0xFFEBEDEF),
                onSurface = Color(0xFFEBEDEF)
            )
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen() {
        var ipAddress by remember { mutableStateOf(sharedPrefs.getString("ip_address", "") ?: "") }
        var port by remember { mutableStateOf(sharedPrefs.getInt("port", 80).toString()) }
        
        var defaultDuplex by remember { mutableStateOf(sharedPrefs.getString("default_duplex", "one-sided") ?: "one-sided") }
        var defaultTray by remember { mutableStateOf(sharedPrefs.getString("default_tray", "auto") ?: "auto") }
        var defaultQuality by remember { mutableStateOf(sharedPrefs.getString("default_quality", "600dpi") ?: "600dpi") }

        var status by remember { mutableStateOf("") }
        var isScanning by remember { mutableStateOf(false) }
        var scanResults by remember { mutableStateOf(emptyList<String>()) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(getString(R.string.settings_title)) },
                    navigationIcon = {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Connection & Network
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getString(R.string.section_connection),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text(getString(R.string.ip_address_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text(getString(R.string.port_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { saveSettings(ipAddress, port, defaultDuplex, defaultTray, defaultQuality) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(getString(R.string.save_settings))
                            }

                            Button(
                                onClick = { testConnection(ipAddress, port) { status = it } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(getString(R.string.test_connection))
                            }
                        }

                        if (status.isNotEmpty()) {
                            val isSuccess = status.contains("success", ignoreCase = true) || status.contains("erfolgreich", ignoreCase = true)
                            Text(
                                text = status,
                                modifier = Modifier.padding(top = 8.dp),
                                color = if (isSuccess) Color(0xFF2ECC71) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Card 2: Default Print Options
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = getString(R.string.section_default_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        // Duplex Dropdown
                        DropdownSetting(
                            label = getString(R.string.label_duplex),
                            selectedValue = defaultDuplex,
                            options = listOf(
                                "one-sided" to getString(R.string.duplex_off),
                                "two-sided-long-edge" to getString(R.string.duplex_long_edge),
                                "two-sided-short-edge" to getString(R.string.duplex_short_edge)
                            ),
                            onSelected = { defaultDuplex = it }
                        )

                        // Tray Dropdown
                        DropdownSetting(
                            label = getString(R.string.label_tray),
                            selectedValue = defaultTray,
                            options = listOf(
                                "auto" to getString(R.string.tray_auto),
                                "tray-1" to getString(R.string.tray_1),
                                "tray-2" to getString(R.string.tray_2),
                                "by-pass-tray" to getString(R.string.tray_manual)
                            ),
                            onSelected = { defaultTray = it }
                        )

                        // Quality Dropdown
                        DropdownSetting(
                            label = getString(R.string.label_quality),
                            selectedValue = defaultQuality,
                            options = listOf(
                                "300dpi" to getString(R.string.quality_eco),
                                "600dpi" to getString(R.string.quality_normal),
                                "1200dpi" to getString(R.string.quality_high)
                            ),
                            onSelected = { defaultQuality = it }
                        )
                    }
                }

                // Card 3: Tools & Diagnostics
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getString(R.string.section_tools),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        Button(
                            onClick = { printTestPage(ipAddress, port) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(getString(R.string.print_test_page))
                        }

                        Button(
                            onClick = {
                                val ip = ipAddress.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
                                val intent = Intent(Intent.ACTION_VIEW, "http://$ip".toUri())
                                startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(getString(R.string.open_web_interface))
                        }

                        Button(
                            onClick = {
                                isScanning = true
                                lifecycleScope.launch {
                                    val results = NetworkUtils.scanSubnet(this@SettingsActivity, port.toIntOrNull() ?: 80)
                                    scanResults = results
                                    isScanning = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(getString(R.string.scanning))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(getString(R.string.scan_subnet))
                            }
                        }

                        if (scanResults.isNotEmpty()) {
                            Text(
                                getString(R.string.found_printers),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            scanResults.forEach { result ->
                                ListItem(
                                    headlineContent = { Text(result) },
                                    modifier = Modifier.clickable { 
                                        ipAddress = result 
                                        port = "80"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DropdownSetting(
        label: String,
        selectedValue: String,
        options: List<Pair<String, String>>,
        onSelected: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        val currentDisplay = options.firstOrNull { it.first == selectedValue }?.second ?: selectedValue

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentDisplay,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, display) ->
                    DropdownMenuItem(
                        text = { Text(display) },
                        onClick = {
                            onSelected(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    private fun saveSettings(ip: String, portStr: String, duplex: String, tray: String, quality: String) {
        val port = portStr.toIntOrNull() ?: 80
        sharedPrefs.edit {
            putString("ip_address", ip)
            putInt("port", port)
            putString("default_duplex", duplex)
            putString("default_tray", tray)
            putString("default_quality", quality)
        }
        Toast.makeText(this, getString(R.string.save_settings), Toast.LENGTH_SHORT).show()
    }

    private fun testConnection(ipRaw: String, portStr: String, onResult: (String) -> Unit) {
        val ip = ipRaw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
        
        val port = portStr.toIntOrNull() ?: 631
        if (ip.isBlank()) {
            Toast.makeText(this, "Please enter an IP address", Toast.LENGTH_SHORT).show()
            return
        }

        onResult(getString(R.string.testing))
        lifecycleScope.launch {
            val wifiNetwork = NetworkUtils.getWifiNetwork(this@SettingsActivity)
            val localIp = NetworkUtils.getLocalIpAddress(this@SettingsActivity)
            Log.d(TAG, "Device: ${Build.MODEL}, Local IP: $localIp, WiFi: ${wifiNetwork != null}")

            val success = withContext(Dispatchers.IO) {
                try {
                    val address = InetAddress.getByName(ip)
                    if (wifiNetwork != null) {
                        Log.d(TAG, "Requesting socket from WiFi network...")
                        val s = Socket()
                        wifiNetwork.bindSocket(s)
                        s.use { 
                            it.connect(InetSocketAddress(address, port), 5000)
                        }
                    } else {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(address, port), 5000)
                        }
                    }
                    Log.d(TAG, "Socket connection success. Proceeding to IPP...")
                    testIppConnection(wifiNetwork, ip, port)
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed: ${e.message}")
                    false
                }
            }
            onResult(if (success) getString(R.string.connection_success) else getString(R.string.connection_failed))
        }
    }

    private fun printTestPage(ipRaw: String, portStr: String) {
        val ip = ipRaw.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        val port = portStr.toIntOrNull() ?: 80
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val wifiNetwork = NetworkUtils.getWifiNetwork(this@SettingsActivity)
                val protocol = if (port == 443) "https" else "http"
                val url = URL("$protocol://$ip:$port/ipp")
                
                Log.d(TAG, "Sending Drucktest to: $url (WiFi: ${wifiNetwork != null})")

                val connection = if (wifiNetwork != null) {
                    wifiNetwork.openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
                } else {
                    url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
                }

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/ipp")
                
                connection.outputStream.use { os ->
                    // IPP Print-Job
                    os.write(byteArrayOf(0x01, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01))
                    os.write(0x01) // Operation Attributes
                    writeIppAttribute(os, 0x47, "attributes-charset", "utf-8")
                    writeIppAttribute(os, 0x48, "attributes-natural-language", "en")
                    writeIppAttribute(os, 0x45, "printer-uri", "ipp://$ip/ipp")
                    writeIppAttribute(os, 0x42, "job-name", getString(R.string.print_test_page))
                    os.write(0x03) // End
                    
                    // Simple text data
                    os.write(getString(R.string.test_page_text).toByteArray())
                }
                
                val code = connection.responseCode
                Log.d(TAG, "Drucktest Response Code: $code")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.test_print_result, code), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Drucktest failed: ${e.message}")
                if (port == 631) {
                    Log.d(TAG, "Retrying Drucktest on port 80")
                    printTestPage(ipRaw, "80")
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, getString(R.string.test_print_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private suspend fun testIppConnection(network: Network?, ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val protocol = if (port == 443) "https" else "http"
            val url = URL("$protocol://$ip:$port/ipp")
            Log.d(TAG, "Phase 2: Testing IPP connection to: $url")
            
            val connection = if (network != null) {
                network.openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
            } else {
                url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            }

            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
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
            
            val responseCode = connection.responseCode
            Log.d(TAG, "IPP Response Code: $responseCode")
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "IPP Connection Failed: ${e.message}")
            if (port == 631) return@withContext testIppConnection(network, ip, 80)
            false
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

    private fun tryConnect(network: Network?, host: String, port: Int): Boolean {
        return try {
            Log.d(TAG, "Trying raw socket connect to $host:$port")
            
            // Parse IP manually to avoid any DNS lookups
            val parts = host.split(".").map { it.toInt().toByte() }.toByteArray()
            val address = InetAddress.getByAddress(parts)
            
            val socket = if (network != null) {
                val s = Socket()
                network.bindSocket(s)
                s
            } else {
                Socket()
            }
            
            socket.connect(InetSocketAddress(address, port), 5000)
            socket.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Raw socket failed: ${e.message}")
            false
        }
    }
}