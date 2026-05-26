package com.bledroid.core

class BluetoothUnavailableException(
    message: String = "Bluetooth is not available on this device.",
    cause: Throwable? = null,
) : BleDroidException(message, cause)
