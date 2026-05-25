package com.bledroid.core

import android.bluetooth.BluetoothAdapter

/** Bluetooth adapter power state. */
enum class BluetoothAdapterPowerState {
    Off,
    TurningOn,
    On,
    TurningOff,
    Unknown,
}

/** Discoverability/connectability mode of the local adapter. */
enum class BluetoothScanMode {
    None,
    Connectable,
    ConnectableDiscoverable,
    Unknown,
}

/** Pairing variant used during pairing request flows. */
enum class BluetoothPairingVariant {
    Pin,
    Passkey,
    PasskeyConfirmation,
    Consent,
    DisplayPasskey,
    DisplayPin,
    OobConsent,
    Pin16Digits,
    Unknown,
}

sealed interface BluetoothBroadcastEvent {
    data class AdapterStateChanged(
        val state: BluetoothAdapterPowerState,
        val previousState: BluetoothAdapterPowerState,
    ) : BluetoothBroadcastEvent

    data class ScanModeChanged(
        val mode: BluetoothScanMode,
        val previousMode: BluetoothScanMode,
    ) : BluetoothBroadcastEvent

    data object DiscoveryStarted : BluetoothBroadcastEvent

    data object DiscoveryFinished : BluetoothBroadcastEvent

    data class DeviceFound(
        val device: BluetoothDeviceInfo,
        val rssi: Short?,
    ) : BluetoothBroadcastEvent

    data class BondStateChanged(
        val device: BluetoothDeviceInfo,
        val state: BluetoothBondState,
        val previousState: BluetoothBondState,
    ) : BluetoothBroadcastEvent

    data class AclConnected(
        val device: BluetoothDeviceInfo,
    ) : BluetoothBroadcastEvent

    data class AclDisconnected(
        val device: BluetoothDeviceInfo,
    ) : BluetoothBroadcastEvent

    data class AclDisconnectRequested(
        val device: BluetoothDeviceInfo,
    ) : BluetoothBroadcastEvent

    data class PairingRequest(
        val device: BluetoothDeviceInfo,
        val pairingVariant: BluetoothPairingVariant,
    ) : BluetoothBroadcastEvent

    data class NameChanged(
        val name: String,
    ) : BluetoothBroadcastEvent
}

internal fun Int.toBluetoothAdapterPowerState(): BluetoothAdapterPowerState = when (this) {
    BluetoothAdapter.STATE_OFF -> BluetoothAdapterPowerState.Off
    BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterPowerState.TurningOn
    BluetoothAdapter.STATE_ON -> BluetoothAdapterPowerState.On
    BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterPowerState.TurningOff
    else -> BluetoothAdapterPowerState.Unknown
}

internal fun Int.toBluetoothScanMode(): BluetoothScanMode = when (this) {
    BluetoothAdapter.SCAN_MODE_NONE -> BluetoothScanMode.None
    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> BluetoothScanMode.Connectable
    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> BluetoothScanMode.ConnectableDiscoverable
    else -> BluetoothScanMode.Unknown
}
