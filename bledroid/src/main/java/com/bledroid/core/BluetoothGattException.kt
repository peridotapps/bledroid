package com.bledroid.core

class BluetoothGattException(
    val status: Int,
    operation: String,
    cause: Throwable? = null,
) : BleDroidException("$operation failed with GATT status $status.", cause)
