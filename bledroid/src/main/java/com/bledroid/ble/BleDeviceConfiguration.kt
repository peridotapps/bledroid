package com.bledroid.ble

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class BleDeviceConfiguration(
    val deviceTypeTag: String? = null,
    val defaultAutoConnect: Boolean = false,
    val connectTimeout: Duration = 15.seconds,
    val discoverServicesTimeout: Duration = 10.seconds,
    val readTimeout: Duration = 10.seconds,
    val writeTimeout: Duration = 10.seconds,
    val rssiTimeout: Duration = 10.seconds,
    val notificationOperationTimeout: Duration = 10.seconds,
    val notificationResponseTimeout: Duration = 10.seconds,
    val preferredMtu: Int? = null,
    val preferredConnectionPriority: Int? = null,
    val preferredPhy: BlePreferredPhy? = null,
    val companionAssociationTimeout: Duration = 30.seconds,
    val storeBondedConnectionMetadata: Boolean = true,
    val autoReconnectOnUnexpectedDisconnect: Boolean = true,
)
