package com.example.nearby.network.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WifiDirectState {
    object Unavailable : WifiDirectState()
    object Disabled : WifiDirectState()
    object Idle : WifiDirectState()
    object Searching : WifiDirectState()
    data class DevicesFound(val count: Int) : WifiDirectState()
    object Connecting : WifiDirectState()
    data class Connected(val isGroupOwner: Boolean, val groupOwnerAddress: String?) : WifiDirectState()
    object Disconnected : WifiDirectState()
    data class Error(val message: String) : WifiDirectState()
}

class WifiDirectManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var isReceiverRegistered = false

    private val _networkState = MutableStateFlow<WifiDirectState>(
        if (wifiP2pManager != null) WifiDirectState.Idle else WifiDirectState.Unavailable
    )
    val networkState: StateFlow<WifiDirectState> = _networkState.asStateFlow()

    private val _rawPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val rawPeers: StateFlow<List<WifiP2pDevice>> = _rawPeers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList: WifiP2pDeviceList? ->
        val devices = peerList?.deviceList?.toList() ?: emptyList()
        _rawPeers.value = devices
        if (devices.isNotEmpty() && _networkState.value is WifiDirectState.Searching) {
            _networkState.value = WifiDirectState.DevicesFound(devices.size)
        }
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        _connectionInfo.value = info
        if (info != null && info.groupFormed) {
            val ownerAddress = info.groupOwnerAddress?.hostAddress
            _networkState.value = WifiDirectState.Connected(
                isGroupOwner = info.isGroupOwner,
                groupOwnerAddress = ownerAddress
            )
        } else {
            _networkState.value = WifiDirectState.Disconnected
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        if (_networkState.value == WifiDirectState.Disabled) {
                            _networkState.value = WifiDirectState.Idle
                        }
                    } else {
                        _networkState.value = WifiDirectState.Disabled
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    } else {
                        _networkState.value = WifiDirectState.Disconnected
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Local device status changed
                }
            }
        }
    }

    fun initialize() {
        if (wifiP2pManager != null && channel == null) {
            channel = wifiP2pManager.initialize(context, context.mainLooper, null)
            registerReceiver()
        }
    }

    fun registerReceiver() {
        if (!isReceiverRegistered) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, intentFilter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                // Ignore registration errors
            }
        }
    }

    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (wifiP2pManager == null || channel == null) {
            _networkState.value = WifiDirectState.Unavailable
            return
        }

        if (!hasRequiredPermissions()) {
            _networkState.value = WifiDirectState.Error("Wi-Fi permissions required for nearby discovery")
            return
        }

        _networkState.value = WifiDirectState.Searching
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _networkState.value = WifiDirectState.Searching
            }

            override fun onFailure(reasonCode: Int) {
                val errorMsg = when (reasonCode) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct unsupported on this device"
                    WifiP2pManager.BUSY -> "Wi-Fi Direct framework is busy"
                    WifiP2pManager.ERROR -> "Wi-Fi Direct discovery failed"
                    else -> "Peer discovery error: $reasonCode"
                }
                _networkState.value = WifiDirectState.Error(errorMsg)
            }
        })
    }

    fun stopDiscovery() {
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.stopPeerDiscovery(channel, null)
            if (_networkState.value is WifiDirectState.Searching) {
                _networkState.value = WifiDirectState.Idle
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestPeers() {
        if (wifiP2pManager != null && channel != null && hasRequiredPermissions()) {
            wifiP2pManager.requestPeers(channel, peerListListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (wifiP2pManager == null || channel == null) {
            onFailure("Wi-Fi Direct unavailable")
            return
        }
        if (!hasRequiredPermissions()) {
            onFailure("Missing Wi-Fi permissions")
            return
        }

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }

        _networkState.value = WifiDirectState.Connecting
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                _networkState.value = WifiDirectState.Error("Connection initiation failed: $reason")
                onFailure("Failed with code $reason")
            }
        })
    }

    fun requestConnectionInfo() {
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.requestConnectionInfo(channel, connectionInfoListener)
        }
    }

    fun disconnect() {
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _networkState.value = WifiDirectState.Disconnected
                }
                override fun onFailure(reason: Int) {
                    _networkState.value = WifiDirectState.Disconnected
                }
            })
        }
    }
}
