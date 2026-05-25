package com.bledroid.core

class MissingBluetoothPermissionException(
    message: String,
    cause: Throwable? = null,
) : BledroidException(message, cause)
