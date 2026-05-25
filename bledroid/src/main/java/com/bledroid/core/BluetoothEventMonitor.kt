package com.bledroid.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits Bluetooth system broadcast events such as adapter state changes, discovery, and bond updates.
 */
interface BluetoothEventMonitor {
    fun events(): Flow<BluetoothBroadcastEvent>
}

internal class BluetoothEventMonitorImpl(
    private val context: Context,
) : BluetoothEventMonitor {
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun events(): Flow<BluetoothBroadcastEvent> = callbackFlow {
        val appContext = context.applicationContext

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val event = intent.toBluetoothBroadcastEvent(action)
                if (event != null) trySend(event)
            }
        }

        val filter = intentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    @SuppressLint("InlinedApi")
    private fun intentFilter(): IntentFilter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)

        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
    }
}

@SuppressLint("InlinedApi", "MissingPermission")
internal fun Intent.toBluetoothBroadcastEvent(action: String): BluetoothBroadcastEvent? = when (action) {
    BluetoothAdapter.ACTION_STATE_CHANGED -> BluetoothBroadcastEvent.AdapterStateChanged(
        state = getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR).toBluetoothAdapterPowerState(),
        previousState = getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR).toBluetoothAdapterPowerState(),
    )

    BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> BluetoothBroadcastEvent.ScanModeChanged(
        mode = getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR).toBluetoothScanMode(),
        previousMode = getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, BluetoothAdapter.ERROR).toBluetoothScanMode(),
    )

    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> BluetoothBroadcastEvent.DiscoveryStarted

    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> BluetoothBroadcastEvent.DiscoveryFinished

    BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED -> {
        val name = getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME) ?: return null
        BluetoothBroadcastEvent.NameChanged(name)
    }

    BluetoothDevice.ACTION_FOUND -> {
        val device = getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
        val rssi = if (hasExtra(BluetoothDevice.EXTRA_RSSI)) {
            getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).takeIf { it != Short.MIN_VALUE }
        } else {
            null
        }
        BluetoothBroadcastEvent.DeviceFound(device = device.toDeviceInfo(), rssi = rssi)
    }

    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
        val device = getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
        BluetoothBroadcastEvent.BondStateChanged(
            device = device.toDeviceInfo(),
            state = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR).toBluetoothBondState(),
            previousState = getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR).toBluetoothBondState(),
        )
    }

    BluetoothDevice.ACTION_ACL_CONNECTED -> {
        val device = getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
        BluetoothBroadcastEvent.AclConnected(device.toDeviceInfo())
    }

    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
        val device = getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
        BluetoothBroadcastEvent.AclDisconnected(device.toDeviceInfo())
    }

    BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> {
        val device = getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
        BluetoothBroadcastEvent.AclDisconnectRequested(device.toDeviceInfo())
    }

    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
        val device = getParcelableExtraCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return null
        val pairing = getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR).toBluetoothPairingVariant()
        BluetoothBroadcastEvent.PairingRequest(device.toDeviceInfo(), pairing)
    }

    else -> null
}

@Suppress("DEPRECATION")
private inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? =
    when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
        else -> getParcelableExtra(key)
    }

@SuppressLint("InlinedApi")
private fun Int.toBluetoothPairingVariant(): BluetoothPairingVariant = when (this) {
    0 -> BluetoothPairingVariant.Pin
    1 -> BluetoothPairingVariant.Passkey
    2 -> BluetoothPairingVariant.PasskeyConfirmation
    3 -> BluetoothPairingVariant.Consent
    4 -> BluetoothPairingVariant.DisplayPasskey
    5 -> BluetoothPairingVariant.DisplayPin
    6 -> BluetoothPairingVariant.OobConsent
    7 -> BluetoothPairingVariant.Pin16Digits
    else -> BluetoothPairingVariant.Unknown
}
