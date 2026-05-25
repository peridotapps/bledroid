package com.bledroid.core

/** Base exception for library-level Bluetooth failures. */
open class BledroidException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
