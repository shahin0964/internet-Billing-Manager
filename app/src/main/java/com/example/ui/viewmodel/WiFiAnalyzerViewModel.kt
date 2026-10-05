package com.example.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class WifiBand(val title: String, val shortName: String, val minFreq: Int, val maxFreq: Int) {
    BAND_2_4_GHZ("2.4 GHz", "2.4G", 2400, 2495),
    BAND_5_GHZ("5 GHz", "5G", 4900, 5925),
    BAND_6_GHZ("6 GHz", "6G", 5925, 7125)
}

enum class AnalyzerTab(val label: String) {
    CHANNEL_GRAPH("Channel Graph"),
    TIME_GRAPH("Time Graph"),
    BEST_CHANNELS("Channel Rating"),
    ACCESS_POINTS("Access Points")
}

data class ConnectedWifiState(
    val ssid: String = "Not Connected",
    val bssid: String = "",
    val rssi: Int = -100,
    val linkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val channel: Int = 0,
    val bandTitle: String = "",
    val ipAddress: String = "",
    val isConnected: Boolean = false
)

data class AccessPointItem(
    val ssid: String,
    val bssid: String,
    val levelDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val channelWidthMhz: Int,
    val band: WifiBand,
    val security: String,
    val wifiStandard: String,
    val signalQualityPercent: Int,
    val isConnected: Boolean,
    val capabilities: String
)

data class ChannelRating(
    val channel: Int,
    val frequencyMhz: Int,
    val qualityPercent: Int,
    val statusText: String,
    val activeApCount: Int,
    val interferenceScore: Int,
    val isCurrentConnected: Boolean
)

class WiFiAnalyzerViewModel : ViewModel() {

    private val TAG = "WiFiAnalyzerVM"

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _lastScanTime = MutableStateFlow<Long?>(null)
    val lastScanTime: StateFlow<Long?> = _lastScanTime.asStateFlow()

    private val _currentTab = MutableStateFlow(AnalyzerTab.CHANNEL_GRAPH)
    val currentTab: StateFlow<AnalyzerTab> = _currentTab.asStateFlow()

    private val _currentBand = MutableStateFlow(WifiBand.BAND_2_4_GHZ)
    val currentBand: StateFlow<WifiBand> = _currentBand.asStateFlow()

    private val _connectedWifi = MutableStateFlow(ConnectedWifiState())
    val connectedWifi: StateFlow<ConnectedWifiState> = _connectedWifi.asStateFlow()

    private val _allScanResults = MutableStateFlow<List<AccessPointItem>>(emptyList())
    val allScanResults: StateFlow<List<AccessPointItem>> = _allScanResults.asStateFlow()

    // Filtered by current band
    private val _filteredScanResults = MutableStateFlow<List<AccessPointItem>>(emptyList())
    val filteredScanResults: StateFlow<List<AccessPointItem>> = _filteredScanResults.asStateFlow()

    // Band AP counts
    private val _bandCounts = MutableStateFlow(mapOf(
        WifiBand.BAND_2_4_GHZ to 0,
        WifiBand.BAND_5_GHZ to 0,
        WifiBand.BAND_6_GHZ to 0
    ))
    val bandCounts: StateFlow<Map<WifiBand, Int>> = _bandCounts.asStateFlow()

    // Time Graph RSSI History: BSSID -> List of (timestamp, rssi)
    private val _rssiHistory = MutableStateFlow<Map<String, List<Pair<Long, Int>>>>(emptyMap())
    val rssiHistory: StateFlow<Map<String, List<Pair<Long, Int>>>> = _rssiHistory.asStateFlow()

    // Channel Ratings for Current Band
    private val _channelRatings = MutableStateFlow<List<ChannelRating>>(emptyList())
    val channelRatings: StateFlow<List<ChannelRating>> = _channelRatings.asStateFlow()

    private var wifiManager: WifiManager? = null
    private var scanReceiver: BroadcastReceiver? = null
    private var scanPollingJob: Job? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        wifiManager = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        checkPermission(ctx)
        refreshConnectedWifi(ctx)
        registerScanReceiver(ctx)
        startPolling(ctx)
    }

    fun checkPermission(context: Context) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        _hasPermission.value = granted
        if (granted) {
            refreshConnectedWifi(context)
            triggerScan()
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            appContext?.let {
                refreshConnectedWifi(it)
                triggerScan()
            }
        }
    }

    fun setTab(tab: AnalyzerTab) {
        _currentTab.value = tab
    }

    fun setBand(band: WifiBand) {
        _currentBand.value = band
        filterResultsForBand(band, _allScanResults.value, _connectedWifi.value.bssid)
    }

    fun togglePause() {
        val next = !_isPaused.value
        _isPaused.value = next
        if (!next) {
            triggerScan()
        }
    }

    fun triggerScan() {
        if (!_hasPermission.value || wifiManager == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isScanning.value = true
                @Suppress("DEPRECATION")
                val started = wifiManager?.startScan() ?: false
                if (!started) {
                    // Even if startScan returns false due to OS throttling, read existing scan results
                    processScanResults(wifiManager?.scanResults ?: emptyList())
                }
            } catch (e: Exception) {
                Log.w(TAG, "startScan error: ${e.message}")
            } finally {
                delay(1200)
                _isScanning.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshConnectedWifi(context: Context) {
        if (!_hasPermission.value || wifiManager == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = wifiManager?.connectionInfo
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val isWifi = cm?.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

                if (info != null && info.networkId != -1 && isWifi) {
                    val rawSsid = info.ssid ?: ""
                    val cleanSsid = if (rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
                        rawSsid.substring(1, rawSsid.length - 1)
                    } else if (rawSsid == "<unknown ssid>" || rawSsid.isBlank()) {
                        "Connected Wi-Fi"
                    } else {
                        rawSsid
                    }

                    val freq = info.frequency
                    val channel = calculateChannel(freq)
                    val bandTitle = when {
                        freq in 2400..2495 -> "2.4 GHz"
                        freq in 4900..5925 -> "5 GHz"
                        freq in 5925..7125 -> "6 GHz"
                        else -> "Wi-Fi"
                    }

                    val ipInt = info.ipAddress
                    val ipStr = if (ipInt != 0) {
                        String.format(
                            "%d.%d.%d.%d",
                            ipInt and 0xff,
                            (ipInt shr 8) and 0xff,
                            (ipInt shr 16) and 0xff,
                            (ipInt shr 24) and 0xff
                        )
                    } else {
                        "192.168.1.1"
                    }

                    val state = ConnectedWifiState(
                        ssid = cleanSsid,
                        bssid = info.bssid ?: "",
                        rssi = info.rssi,
                        linkSpeedMbps = if (info.linkSpeed > 0) info.linkSpeed else 0,
                        frequencyMhz = freq,
                        channel = channel,
                        bandTitle = bandTitle,
                        ipAddress = ipStr,
                        isConnected = true
                    )
                    _connectedWifi.value = state
                } else {
                    _connectedWifi.value = ConnectedWifiState(isConnected = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error refreshing connected wifi: ${e.message}")
            }
        }
    }

    private fun registerScanReceiver(context: Context) {
        if (scanReceiver != null) return
        scanReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    if (!_isPaused.value && _hasPermission.value && wifiManager != null) {
                        try {
                            val results = wifiManager?.scanResults ?: emptyList()
                            processScanResults(results)
                        } catch (e: SecurityException) {
                            Log.w(TAG, "Permission lost on scan receive: ${e.message}")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)
    }

    private fun startPolling(context: Context) {
        scanPollingJob?.cancel()
        scanPollingJob = viewModelScope.launch {
            while (isActive) {
                if (!_isPaused.value && _hasPermission.value) {
                    refreshConnectedWifi(context)
                    triggerScan()
                }
                delay(4000) // 4 seconds optimized interval
            }
        }
    }

    private fun processScanResults(rawResults: List<ScanResult>) {
        viewModelScope.launch(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            _lastScanTime.value = now

            val connectedBssid = _connectedWifi.value.bssid

            val items = rawResults.map { res ->
                val freq = res.frequency
                val band = when {
                    freq in 2400..2495 -> WifiBand.BAND_2_4_GHZ
                    freq in 4900..5925 -> WifiBand.BAND_5_GHZ
                    freq in 5925..7125 -> WifiBand.BAND_6_GHZ
                    else -> WifiBand.BAND_2_4_GHZ
                }
                val channel = calculateChannel(freq)
                val widthMhz = when (res.channelWidth) {
                    ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                    ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                    ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                    ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
                    else -> 20
                }

                val wifiStd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    when (res.wifiStandard) {
                        ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
                        ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
                        ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
                        ScanResult.WIFI_STANDARD_LEGACY -> "Legacy"
                        else -> if (band == WifiBand.BAND_5_GHZ) "Wi-Fi 5" else "Wi-Fi 4"
                    }
                } else {
                    if (band == WifiBand.BAND_5_GHZ) "Wi-Fi 5" else "Wi-Fi 4"
                }

                val caps = res.capabilities ?: ""
                val sec = when {
                    caps.contains("SAE") || caps.contains("WPA3") -> "WPA3-Personal"
                    caps.contains("WPA2") && caps.contains("WPA-") -> "WPA/WPA2-PSK"
                    caps.contains("WPA2") -> "WPA2-PSK"
                    caps.contains("WPA") -> "WPA-PSK"
                    caps.contains("WEP") -> "WEP"
                    caps.contains("OWE") -> "OWE (Enhanced Open)"
                    else -> "Open (No Password)"
                }

                // Signal Quality % : -30 dBm = 100%, -100 dBm = 0%
                val quality = max(0, min(100, ((res.level + 100) * 100) / 70))

                val isConn = connectedBssid.isNotBlank() && connectedBssid.equals(res.BSSID, ignoreCase = true)

                AccessPointItem(
                    ssid = if (res.SSID.isNullOrBlank()) "Hidden Network" else res.SSID,
                    bssid = res.BSSID ?: "00:00:00:00:00:00",
                    levelDbm = res.level,
                    frequencyMhz = freq,
                    channel = channel,
                    channelWidthMhz = widthMhz,
                    band = band,
                    security = sec,
                    wifiStandard = wifiStd,
                    signalQualityPercent = quality,
                    isConnected = isConn,
                    capabilities = caps
                )
            }.sortedByDescending { it.levelDbm }

            _allScanResults.value = items

            // Calculate band counts
            val b24 = items.count { it.band == WifiBand.BAND_2_4_GHZ }
            val b5 = items.count { it.band == WifiBand.BAND_5_GHZ }
            val b6 = items.count { it.band == WifiBand.BAND_6_GHZ }
            _bandCounts.value = mapOf(
                WifiBand.BAND_2_4_GHZ to b24,
                WifiBand.BAND_5_GHZ to b5,
                WifiBand.BAND_6_GHZ to b6
            )

            // Update Time Graph History (keep 60s sliding window)
            val currentMap = _rssiHistory.value.toMutableMap()
            val timeWindow = 60000L
            val cutoff = now - timeWindow

            items.forEach { ap ->
                val list = currentMap.getOrDefault(ap.bssid, emptyList()).toMutableList()
                list.add(Pair(now, ap.levelDbm))
                currentMap[ap.bssid] = list.filter { it.first >= cutoff }
            }
            // Cleanup stale BSSIDs
            val activeBssids = items.map { it.bssid }.toSet()
            val cleaned = currentMap.filterKeys { it in activeBssids }
            _rssiHistory.value = cleaned

            // Filter for current band and update channel ratings
            filterResultsForBand(_currentBand.value, items, connectedBssid)
        }
    }

    private fun filterResultsForBand(band: WifiBand, allItems: List<AccessPointItem>, connectedBssid: String) {
        val filtered = allItems.filter { it.band == band }
        _filteredScanResults.value = filtered

        // Calculate Best Channel Ratings for selected band
        val channels = when (band) {
            WifiBand.BAND_2_4_GHZ -> (1..14).toList()
            WifiBand.BAND_5_GHZ -> listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 132, 136, 140, 144, 149, 153, 157, 161, 165)
            WifiBand.BAND_6_GHZ -> listOf(1, 5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45, 49, 53, 57, 61, 65, 69, 73, 77, 81, 85, 89, 93)
        }

        val connectedChannel = _connectedWifi.value.channel

        val ratings = channels.map { ch ->
            val chFreq = getFrequencyForChannel(ch, band)
            var interference = 0
            var count = 0

            filtered.forEach { ap ->
                val freqDiff = abs(ap.frequencyMhz - chFreq)
                if (band == WifiBand.BAND_2_4_GHZ) {
                    if (freqDiff <= 22) {
                        // In 2.4G, adjacent channels overlap significantly
                        val weight = max(1, 22 - freqDiff)
                        val signalPower = max(5, 100 + ap.levelDbm) // e.g. -40dBm -> 60, -85dBm -> 15
                        interference += (signalPower * weight) / 10
                        if (freqDiff == 0) count++
                    }
                } else {
                    // 5GHz and 6GHz have discrete non-overlapping channels for standard 20MHz
                    if (freqDiff == 0) {
                        val signalPower = max(5, 100 + ap.levelDbm)
                        interference += signalPower
                        count++
                    } else if (freqDiff <= ap.channelWidthMhz / 2) {
                        interference += (max(5, 100 + ap.levelDbm) * 0.7).toInt()
                    }
                }
            }

            val qualityPercent = max(10, min(100, 100 - (interference * 1.2).toInt()))
            val status = when {
                qualityPercent >= 85 -> "Excellent (Clean)"
                qualityPercent >= 70 -> "Good Channel"
                qualityPercent >= 50 -> "Fair / Moderate"
                else -> "Congested"
            }

            ChannelRating(
                channel = ch,
                frequencyMhz = chFreq,
                qualityPercent = qualityPercent,
                statusText = status,
                activeApCount = count,
                interferenceScore = interference,
                isCurrentConnected = (ch == connectedChannel && _connectedWifi.value.isConnected && _connectedWifi.value.bandTitle == band.title)
            )
        }.sortedWith(compareByDescending<ChannelRating> { it.qualityPercent }.thenBy { it.activeApCount })

        _channelRatings.value = ratings
    }

    override fun onCleared() {
        super.onCleared()
        scanPollingJob?.cancel()
        scanReceiver?.let {
            try {
                appContext?.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun calculateChannel(freq: Int): Int {
            return when {
                freq == 2484 -> 14
                freq in 2412..2472 -> ((freq - 2412) / 5) + 1
                freq in 5170..5825 -> ((freq - 5170) / 5) + 34
                freq in 5825..5885 -> ((freq - 5825) / 5) + 165
                freq in 5925..7125 -> ((freq - 5925) / 5) + 1 // 6 GHz
                else -> 0
            }
        }

        fun getFrequencyForChannel(channel: Int, band: WifiBand): Int {
            return when (band) {
                WifiBand.BAND_2_4_GHZ -> {
                    if (channel == 14) 2484
                    else 2412 + (channel - 1) * 5
                }
                WifiBand.BAND_5_GHZ -> {
                    5170 + (channel - 34) * 5
                }
                WifiBand.BAND_6_GHZ -> {
                    5925 + (channel - 1) * 5
                }
            }
        }
    }
}
