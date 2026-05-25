package com.bledroid.ble

internal interface ConnectionInfoEncryptor {
    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): String
}
