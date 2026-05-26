package com.bledroid.core

class NotConnectedException(
    message: String = "No Bluetooth connection is active.",
) : BleDroidException(message)
