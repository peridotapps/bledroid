package com.bledroid.core

/** Pairing variant used during pairing request flows. */
enum class BluetoothPairingVariant {
    Pin,
    Passkey,
    PasskeyConfirmation,
    Consent,
    DisplayPasskey,
    DisplayPin,
    OobConsent,
    Pin16Digits,
    Unknown,
}
