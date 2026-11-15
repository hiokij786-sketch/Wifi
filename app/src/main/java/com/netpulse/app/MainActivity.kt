package com.netpulse.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity
import java.net.InetSocketAddress
import java.net.Socket
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 17, 34)
    private val panel = Color.rgb(15, 31, 54)
    private val blue = Color.rgb(56, 189, 248)
    private val orange = Color.rgb(251, 146, 60)
    private val white = Color.rgb(235, 244, 255)
    private val muted = Color.rgb(151, 171, 196)
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var diagnosticsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestWifiPermissionIfNeeded()
        refreshInfo()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(content)
        setContentView(scroll)

        addText("NETPULSE", 28f, blue, true)
        addText("WI-FI INTELLIGENCE", 11f, muted, true).letterSpacing = 0.18f
        addSpace(18)
        val hero = card()
        hero.addView(TextView(this).apply {
            text = "Your network. Understood."
            textSize = 21f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })
        statusText = TextView(this).apply {
            text = "Checking connection..."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        }
        hero.addView(statusText)
        content.addView(hero)
        addSpace(14)

        val fix = Button(this).apply {
            text = "ONE-TAP FIX & DIAGNOSE"
            textSize = 14f
            setTextColor(Color.BLACK)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(orange))
            setOnClickListener { runOneTap() }
        }
        content.addView(fix, matchWrap())
        addSpace(8)
        val refresh = Button(this).apply {
            text = "REFRESH WI-FI DETAILS"
            setTextColor(white)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(30, 65, 100)))
            setOnClickListener { refreshInfo() }
        }
        content.addView(refresh, matchWrap())
        addSpace(6)
        val speed = Button(this).apply {
            text = "RUN INTERNET SPEED TEST"
            setTextColor(white)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(30, 65, 100)))
            setOnClickListener { runSpeedTest() }
        }
        content.addView(speed, matchWrap())
        addSpace(16)

        addText("LIVE WI-FI DETAILS", 16f, white, true)
        addSpace(8)
        val details = card()
        val fields = listOf(
            "Network name (SSID)", "Phone IP address", "Router / Gateway",
            "Subnet mask", "DNS servers", "Wi-Fi frequency", "Signal strength",
            "Link speed", "Connection type"
        )
        fields.forEach { key ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(7), 0, dp(7))
            }
            row.addView(TextView(this).apply {
                text = key.uppercase(Locale.getDefault())
                textSize = 10f
                setTextColor(muted)
                letterSpacing = 0.08f
            })
            row.addView(TextView(this).apply {
                tag = "value_$key"
                text = "Reading..."
                textSize = 14f
                setTextColor(white)
                setPadding(0, dp(3), 0, 0)
            })
            details.addView(row)
            val line = View(this).apply { setBackgroundColor(Color.rgb(34, 54, 78)) }
            details.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        }
        content.addView(details)
        addSpace(16)

        addText("NETWORK CHECK", 16f, white, true)
        addSpace(8)
        val diag = card()
        diagnosticsText = TextView(this).apply {
            text = "Run One-Tap Fix & Diagnose to test internet reachability and latency."
            textSize = 13f
            setTextColor(muted)
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        diag.addView(diagnosticsText)
        content.addView(diag)
        addSpace(16)

        addText("QUICK SETTINGS", 16f, white, true)
        addSpace(8)
        val settingsCard = card()
        settingsCard.addView(actionButton("Open Wi-Fi settings") {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        })
        settingsCard.addView(actionButton("Open Internet settings") {
            startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
        })
        settingsCard.addView(actionButton("Open app permissions") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        })
        content.addView(settingsCard)
        addSpace(14)
        addText(
            "IMPORTANT: Android protects Wi-Fi and router settings. NETPULSE can inspect available connection details and run checks, but it cannot silently change router settings, force a DNS change, or repair your ISP connection. Use the settings shortcuts when Android requires your approval.",
            11f, muted, false
        )
    }

    private fun runOneTap() {
        diagnosticsText.text = "Running checks: internet reachability and response time..."
        statusText.text = "Diagnostics running"
        executor.execute {
            val results = mutableListOf<String>()
            val hosts = listOf("1.1.1.1" to 443, "8.8.8.8" to 443, "9.9.9.9" to 443)
            var successes = 0
            val times = mutableListOf<Long>()
            for ((host, port) in hosts) {
                val start = System.currentTimeMillis()
                val ok = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 2500)
                    }
                    true
                } catch (_: Exception) { false }
                val elapsed = System.currentTimeMillis() - start
                if (ok) {
                    successes++
                    times.add(elapsed)
                    results.add("✓ $host reachable — ${elapsed} ms")
                } else {
                    results.add("✕ $host did not respond")
                }
            }
            val avg = if (times.isNotEmpty()) times.average().toLong() else -1L
            val advice = when {
                successes == 0 -> "No test endpoint responded. Check router power, Wi-Fi connection, or ISP status. Open Wi-Fi settings to reconnect."
                avg > 500 -> "High response time detected. Move closer to the router, pause large downloads, and compare 2.4 GHz vs 5 GHz if your router supports both."
                successes < 3 -> "Some endpoints failed. This may be DNS filtering, firewall rules, or a temporary route issue. Compare using another network."
                else -> "Basic connectivity looks healthy. If apps still lag, the issue may be app-specific, server-side, or intermittent."
            }
            runOnUiThread {
                diagnosticsText.text = results.joinToString("\n") +
                    "\n\nEndpoints reachable: $successes / 3" +
                    (if (avg >= 0) "\nAverage TCP connect time: ${avg} ms" else "") +
                    "\n\nASSESSMENT\n$advice\n\nNo device/router configuration was changed automatically."
                statusText.text = if (successes == 3) "Connection checks completed" else "Issues or limitations detected"
            }
        }
    }

    private fun runSpeedTest() {
        diagnosticsText.text = "Testing download speed... please wait."
        statusText.text = "Speed test running"
        executor.execute {
            var result = "Speed test could not complete."
            try {
                val url = URL("https://speed.cloudflare.com/__down?bytes=5000000")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val start = System.nanoTime()
                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                    }
                    total
                }
                val seconds = max(0.001, (System.nanoTime() - start) / 1_000_000_000.0)
                val mbps = (bytes * 8.0 / seconds) / 1_000_000.0
                connection.disconnect()
                result = String.format(Locale.US,
                    "Download: %.2f Mbps\\nData measured: %.2f MB\\nDuration: %.1f seconds\\n\\nThis is a single-server estimate; results vary with server load, Wi-Fi signal and ISP routing.",
                    mbps, bytes / 1_000_000.0, seconds)
            } catch (e: Exception) {
                result = "Speed test failed: ${e.javaClass.simpleName}. Check internet access and try again."
            }
            runOnUiThread {
                diagnosticsText.text = result
                statusText.text = "Speed test completed"
            }
        }
    }

    private fun refreshInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        val props: LinkProperties? = if (network != null) cm.getLinkProperties(network) else null
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiInfo = if (Build.VERSION.SDK_INT >= 29) caps?.transportInfo as? WifiInfo else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
        }

        val ssid = if (hasLocationPermission()) {
            wifiInfo?.ssid?.removeSurrounding("\"")?.takeUnless { it == "<unknown ssid>" } ?: "Unavailable"
        } else "Permission required"
        val rssi = wifiInfo?.rssi ?: -127
        val signal = if (rssi <= -100) "Unavailable" else "$rssi dBm (${signalLabel(rssi)})"
        val frequency = wifiInfo?.frequency?.let {
            when {
                it in 2400..2500 -> "2.4 GHz ($it MHz)"
                it in 4900..5900 -> "5 GHz ($it MHz)"
                it in 5925..7125 -> "6 GHz ($it MHz)"
                else -> "$it MHz"
            }
        } ?: "Unavailable"
        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "Unavailable"
        val ip = props?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress ?: "Unavailable"
        val gateway = props?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "Unavailable"
        val dns = props?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" }?.ifBlank { "Unavailable" } ?: "Unavailable"
        val prefix = props?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.prefixLength
        val subnet = prefix?.let { prefixToMask(it) } ?: "Unavailable"
        val type = when {
            isWifi -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
            else -> "No active internet network"
        }

        putValue("Network name (SSID)", ssid)
        putValue("Phone IP address", ip)
        putValue("Router / Gateway", gateway)
        putValue("Subnet mask", subnet)
        putValue("DNS servers", dns)
        putValue("Wi-Fi frequency", if (isWifi) frequency else "Not on Wi-Fi")
        putValue("Signal strength", if (isWifi) signal else "Not on Wi-Fi")
        putValue("Link speed", if (isWifi) linkSpeed else "Not on Wi-Fi")
        putValue("Connection type", type)
        statusText.text = if (isWifi) "Connected to Wi-Fi • details updated" else type
    }

    private fun putValue(key: String, value: String) {
        val view = content.findViewWithTag<TextView>("value_$key")
        view?.text = value
    }

    private fun requestWifiPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && !hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) refreshInfo()
    }

    private fun signalLabel(rssi: Int) = when {
        rssi >= -55 -> "Excellent"
        rssi >= -67 -> "Good"
        rssi >= -75 -> "Fair"
        else -> "Weak"
    }

    private fun prefixToMask(prefix: Int): String {
        val mask = if (prefix <= 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        return (24 downTo 0 step 8).joinToString(".") { shift -> ((mask shr shift) and 255).toString() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(30, 57, 83))
        }
    }

    private fun addText(value: String, size: Float, color: Int, bold: Boolean) {
        content.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        })
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(blue)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(25, 49, 76)))
        setOnClickListener { action() }
    }

    private fun addSpace(height: Int) {
        content.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(3)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}

