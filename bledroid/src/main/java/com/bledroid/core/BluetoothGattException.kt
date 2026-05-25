package com.bledroid.core

class BluetoothGattException(
    val status: Int,
    operation: String,
    cause: Throwable? = null,
) : BledroidException("$operation failed with GATT status $status.", cause)
