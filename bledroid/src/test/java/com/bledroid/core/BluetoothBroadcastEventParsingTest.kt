package com.bledroid.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothBroadcastEventParsingTest {
    @Test
    fun adapterStateChangedMapsCurrentAndPreviousState() {
        val intent = mockk<Intent>()
        every { intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) } returns BluetoothAdapter.STATE_ON
        every {
            intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR)
        } returns BluetoothAdapter.STATE_TURNING_ON

        val event = intent.toBluetoothBroadcastEvent(BluetoothAdapter.ACTION_STATE_CHANGED)

        assertEquals(
            BluetoothBroadcastEvent.AdapterStateChanged(
                state = BluetoothAdapterPowerState.On,
                previousState = BluetoothAdapterPowerState.TurningOn,
            ),
            event,
        )
    }

    @Test
    fun scanModeChangedMapsCurrentAndPreviousMode() {
        val intent = mockk<Intent>()
        every {
            intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
        } returns BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
        every {
            intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, BluetoothAdapter.ERROR)
        } returns BluetoothAdapter.SCAN_MODE_CONNECTABLE

        val event = intent.toBluetoothBroadcastEvent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)

        assertEquals(
            BluetoothBroadcastEvent.ScanModeChanged(
                mode = BluetoothScanMode.ConnectableDiscoverable,
                previousMode = BluetoothScanMode.Connectable,
            ),
            event,
        )
    }

    @Test
    fun discoveryActionsMapToSingletonEvents() {
        val intent = mockk<Intent>()

        assertEquals(
            BluetoothBroadcastEvent.DiscoveryStarted,
            intent.toBluetoothBroadcastEvent(BluetoothAdapter.ACTION_DISCOVERY_STARTED),
        )
        assertEquals(
            BluetoothBroadcastEvent.DiscoveryFinished,
            intent.toBluetoothBroadcastEvent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED),
        )
    }

    @Test
    fun localNameChangedMapsNameWhenPresent() {
        val intent = mockk<Intent>()
        every { intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME) } returns "Test Adapter"

        val event = intent.toBluetoothBroadcastEvent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)

        assertEquals(BluetoothBroadcastEvent.NameChanged("Test Adapter"), event)
    }

    @Test
    fun localNameChangedReturnsNullWhenNameMissing() {
        val intent = mockk<Intent>()
        every { intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME) } returns null

        assertNull(intent.toBluetoothBroadcastEvent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED))
    }

    @Test
    fun deviceFoundMapsDeviceAndRssi() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)
        every { intent.hasExtra(BluetoothDevice.EXTRA_RSSI) } returns true
        every { intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE) } returns (-54).toShort()

        val event = intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_FOUND)

        assertEquals(
            BluetoothBroadcastEvent.DeviceFound(
                device = expectedDeviceInfo(),
                rssi = (-54).toShort(),
            ),
            event,
        )
    }

    @Test
    fun deviceFoundReturnsNullRssiWhenRssiExtraIsMissing() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)
        every { intent.hasExtra(BluetoothDevice.EXTRA_RSSI) } returns false

        val event = intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_FOUND)

        assertEquals(
            BluetoothBroadcastEvent.DeviceFound(
                device = expectedDeviceInfo(),
                rssi = null,
            ),
            event,
        )
    }

    @Test
    fun bondStateChangedMapsDeviceAndStates() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)
        every {
            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
        } returns BluetoothDevice.BOND_BONDED
        every {
            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
        } returns BluetoothDevice.BOND_BONDING

        val event = intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_BOND_STATE_CHANGED)

        assertEquals(
            BluetoothBroadcastEvent.BondStateChanged(
                device = expectedDeviceInfo(),
                state = BluetoothBondState.Bonded,
                previousState = BluetoothBondState.Bonding,
            ),
            event,
        )
    }

    @Test
    fun pairingRequestMapsPairingVariant() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)
        every {
            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
        } returns 2

        val event = intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_PAIRING_REQUEST)

        assertEquals(
            BluetoothBroadcastEvent.PairingRequest(
                device = expectedDeviceInfo(),
                pairingVariant = BluetoothPairingVariant.PasskeyConfirmation,
            ),
            event,
        )
    }

    @Test
    fun aclEventsMapToDeviceEvents() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)

        assertEquals(
            BluetoothBroadcastEvent.AclConnected(expectedDeviceInfo()),
            intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_ACL_CONNECTED),
        )
        assertEquals(
            BluetoothBroadcastEvent.AclDisconnected(expectedDeviceInfo()),
            intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_ACL_DISCONNECTED),
        )
        assertEquals(
            BluetoothBroadcastEvent.AclDisconnectRequested(expectedDeviceInfo()),
            intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED),
        )
    }

    @Test
    fun pairingRequestMapsAllKnownVariantsAndUnknownFallback() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)

        val mapping = mapOf(
            0 to BluetoothPairingVariant.Pin,
            1 to BluetoothPairingVariant.Passkey,
            2 to BluetoothPairingVariant.PasskeyConfirmation,
            3 to BluetoothPairingVariant.Consent,
            4 to BluetoothPairingVariant.DisplayPasskey,
            5 to BluetoothPairingVariant.DisplayPin,
            6 to BluetoothPairingVariant.OobConsent,
            7 to BluetoothPairingVariant.Pin16Digits,
            999 to BluetoothPairingVariant.Unknown,
        )

        mapping.forEach { (variant, expected) ->
            every {
                intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
            } returns variant
            assertEquals(
                BluetoothBroadcastEvent.PairingRequest(
                    device = expectedDeviceInfo(),
                    pairingVariant = expected,
                ),
                intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_PAIRING_REQUEST),
            )
        }
    }

    @Test
    fun actionRequiringDeviceReturnsNullWhenDeviceExtraMissing() {
        val intent = mockk<Intent>()
        every { intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns null
        every { intent.hasExtra(BluetoothDevice.EXTRA_RSSI) } returns false
        every {
            intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
        } returns BluetoothDevice.BOND_BONDED
        every {
            intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
        } returns BluetoothDevice.BOND_NONE
        every {
            intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR)
        } returns 0

        assertNull(intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_FOUND))
        assertNull(intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        assertNull(intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_ACL_CONNECTED))
        assertNull(intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_ACL_DISCONNECTED))
        assertNull(intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED))
        assertNull(intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_PAIRING_REQUEST))
    }

    @Test
    fun actionFoundTreatsSentinelRssiAsNull() {
        val device = mockDevice()
        val intent = mockk<Intent>()
        everyDeviceExtra(intent, device)
        every { intent.hasExtra(BluetoothDevice.EXTRA_RSSI) } returns true
        every { intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE) } returns Short.MIN_VALUE

        val event = intent.toBluetoothBroadcastEvent(BluetoothDevice.ACTION_FOUND)
        assertEquals(
            BluetoothBroadcastEvent.DeviceFound(
                device = expectedDeviceInfo(),
                rssi = null,
            ),
            event,
        )
    }

    @Test
    fun unknownActionReturnsNull() {
        val intent = mockk<Intent>()

        assertNull(intent.toBluetoothBroadcastEvent("com.example.UNKNOWN"))
    }

    private fun mockDevice(): BluetoothDevice {
        val device = mockk<BluetoothDevice>()
        every { device.name } returns "Sensor"
        every { device.address } returns "01:23:45:67:89:AB"
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED
        return device
    }

    private fun expectedDeviceInfo(): BluetoothDeviceInfo =
        BluetoothDeviceInfo(
            name = "Sensor",
            address = "01:23:45:67:89:AB",
            transport = BluetoothTransport.Ble,
            bondState = BluetoothBondState.Bonded,
        )

    @Suppress("DEPRECATION")
    private fun everyDeviceExtra(intent: Intent, device: BluetoothDevice) {
        every { intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) } returns device
    }
}
