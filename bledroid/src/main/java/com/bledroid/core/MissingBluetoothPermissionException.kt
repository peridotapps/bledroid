package com.bledroid.core

class MissingBluetoothPermissionException(
    message: String,
    cause: Throwable? = null,
) : BleDroidException(message, cause)
