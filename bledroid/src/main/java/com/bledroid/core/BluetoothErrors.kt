package com.bledroid.core

/** Base exception for library-level Bluetooth failures. */
open class BledroidException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BluetoothUnavailableException(
    message: String = "Bluetooth is not available on this device.",
    cause: Throwable? = null,
) : BledroidException(message, cause)

class BluetoothDisabledException(
    message: String = "Bluetooth is disabled.",
) : BledroidException(message)

class MissingBluetoothPermissionException(
    message: String,
    cause: Throwable? = null,
) : BledroidException(message, cause)

class NotConnectedException(
    message: String = "No Bluetooth connection is active.",
) : BledroidException(message)

class BluetoothGattException(
    val status: Int,
    operation: String,
    cause: Throwable? = null,
) : BledroidException("$operation failed with GATT status $status.", cause)
