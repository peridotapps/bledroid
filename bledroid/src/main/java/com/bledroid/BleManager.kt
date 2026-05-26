package com.bledroid

import android.annotation.SuppressLint
import android.companion.AssociationRequest
import android.content.Context
import android.content.IntentSender
import com.bledroid.ble.BleClient
import com.bledroid.ble.BleClientImpl
import com.bledroid.ble.BleDeviceConfiguration
import com.bledroid.ble.BlePreferredPhy
import com.bledroid.ble.BleScanner
import com.bledroid.ble.BleScannerImpl
import com.bledroid.companion.BleCompanionDeviceManager
import com.bledroid.companion.BleCompanionDeviceManagerImpl
import com.bledroid.core.AppContextProvider
import com.bledroid.core.BluetoothDeviceInfo
import com.bledroid.core.BluetoothEventMonitor
import com.bledroid.core.BluetoothEventMonitorImpl
import com.bledroid.core.bluetoothManager
import com.bledroid.core.requireAdapter
import com.bledroid.core.toDeviceInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Entry point for creating Bluetooth scanners and clients. */
class BleManager private constructor(
    private val appContext: Context = AppContextProvider.get(),
    private val deviceConfiguration: BleDeviceConfiguration,
) {
    val bleScanner: BleScanner = BleScannerImpl(appContext)
    val eventMonitor: BluetoothEventMonitor = BluetoothEventMonitorImpl(appContext)
    val companionDeviceManager: BleCompanionDeviceManager =
        BleCompanionDeviceManagerImpl(appContext)
    private val singletonClient: BleClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BleClientImpl(
            context = appContext,
            configuration = deviceConfiguration,
        )
    }

    fun client(): BleClient = singletonClient

    fun isBluetoothAvailable(): Boolean = runCatching {
        appContext.bluetoothManager().requireAdapter()
    }.isSuccess

    fun isBluetoothEnabled(): Boolean = runCatching {
        appContext.bluetoothManager().requireAdapter().isEnabled
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun bondedDevices(): Set<BluetoothDeviceInfo> = runCatching {
        appContext.bluetoothManager()
            .requireAdapter()
            .bondedDevices
            .map { it.toDeviceInfo() }
            .toSet()
    }.getOrDefault(emptySet())

    suspend fun requestCompanionAssociation(
        request: AssociationRequest,
        timeout: Duration? = null,
    ): IntentSender = companionDeviceManager.requestAssociation(
        request = request,
        timeout = timeout ?: deviceConfiguration.companionAssociationTimeout,
    )

    companion object {
        fun initialize(context: Context) {
            AppContextProvider.initialize(context)
        }

        fun builder(): Builder = Builder()
    }

    class Builder {
        private var appContext: Context? = null
        private var deviceTypeTag: String? = null
        private var defaultAutoConnect: Boolean = false
        private var connectTimeout: Duration = 15.seconds
        private var discoverServicesTimeout: Duration = 10.seconds
        private var readTimeout: Duration = 10.seconds
        private var writeTimeout: Duration = 10.seconds
        private var rssiTimeout: Duration = 10.seconds
        private var notificationOperationTimeout: Duration = 10.seconds
        private var notificationResponseTimeout: Duration = 10.seconds
        private var preferredMtu: Int? = null
        private var preferredConnectionPriority: Int? = null
        private var preferredPhy: BlePreferredPhy? = null
        private var companionAssociationTimeout: Duration = 30.seconds
        private var storeBondedConnectionMetadata: Boolean = true
        private var autoReconnectOnUnexpectedDisconnect: Boolean = true

        fun applicationContext(context: Context) = apply {
            AppContextProvider.initialize(context)
            appContext = context.applicationContext
        }

        fun deviceTypeTag(value: String?) = apply { deviceTypeTag = value }

        fun defaultAutoConnect(value: Boolean) = apply { defaultAutoConnect = value }

        fun connectTimeout(value: Duration) = apply { connectTimeout = value }

        fun discoverServicesTimeout(value: Duration) = apply { discoverServicesTimeout = value }

        fun readTimeout(value: Duration) = apply { readTimeout = value }

        fun writeTimeout(value: Duration) = apply { writeTimeout = value }

        fun rssiTimeout(value: Duration) = apply { rssiTimeout = value }

        fun notificationOperationTimeout(value: Duration) = apply {
            notificationOperationTimeout = value
        }

        fun notificationResponseTimeout(value: Duration) = apply {
            notificationResponseTimeout = value
        }

        fun preferredMtu(value: Int?) = apply { preferredMtu = value }

        fun preferredConnectionPriority(value: Int?) = apply { preferredConnectionPriority = value }

        fun preferredPhy(
            txPhy: Int,
            rxPhy: Int,
            phyOptions: Int = 0,
        ) = apply {
            preferredPhy = BlePreferredPhy(
                txPhy = txPhy,
                rxPhy = rxPhy,
                phyOptions = phyOptions,
            )
        }

        fun clearPreferredPhy() = apply { preferredPhy = null }

        fun companionAssociationTimeout(value: Duration) = apply {
            companionAssociationTimeout = value
        }

        fun storeBondedConnectionMetadata(value: Boolean) =
            apply { storeBondedConnectionMetadata = value }

        fun autoReconnectOnUnexpectedDisconnect(value: Boolean) = apply {
            autoReconnectOnUnexpectedDisconnect = value
        }

        fun build(): BleManager {
            val resolvedContext = appContext ?: AppContextProvider.get()
            return BleManager(
                appContext = resolvedContext,
                deviceConfiguration = BleDeviceConfiguration(
                    deviceTypeTag = deviceTypeTag,
                    defaultAutoConnect = defaultAutoConnect,
                    connectTimeout = connectTimeout,
                    discoverServicesTimeout = discoverServicesTimeout,
                    readTimeout = readTimeout,
                    writeTimeout = writeTimeout,
                    rssiTimeout = rssiTimeout,
                    notificationOperationTimeout = notificationOperationTimeout,
                    notificationResponseTimeout = notificationResponseTimeout,
                    preferredMtu = preferredMtu,
                    preferredConnectionPriority = preferredConnectionPriority,
                    preferredPhy = preferredPhy,
                    companionAssociationTimeout = companionAssociationTimeout,
                    storeBondedConnectionMetadata = storeBondedConnectionMetadata,
                    autoReconnectOnUnexpectedDisconnect = autoReconnectOnUnexpectedDisconnect,
                ),
            )
        }
    }
}
