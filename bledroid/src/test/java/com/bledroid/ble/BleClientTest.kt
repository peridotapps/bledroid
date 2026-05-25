package com.bledroid.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import com.bledroid.core.BleCharacteristicId
import com.bledroid.core.BluetoothConnectionState
import com.bledroid.core.BluetoothConnectionStateConnected
import com.bledroid.core.BluetoothConnectionStateDisconnected
import com.bledroid.core.BluetoothConnectionStateDisconnecting
import com.bledroid.core.BluetoothGattException
import com.bledroid.core.BluetoothUnavailableException
import com.bledroid.core.MissingBluetoothPermissionException
import com.bledroid.core.NotConnectedException
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleClientTest {
    @Test
    fun connectFailsWhenBluetoothConnectPermissionIsMissing() = runTest {
        val client: BleClient = BleClientImpl(
            context = mockDeniedContext(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.connect(address = "00:11:22:33:44:55", timeout = 100.milliseconds)
            fail("Expected permission failure.")
        } catch (error: MissingBluetoothPermissionException) {
            assertTrue(error.message.orEmpty().contains("Missing Bluetooth connect permission"))
        }
    }

    @Test
    fun writeClearsCharacteristicWritingStateWhenWriteFailsBeforeConnection() = runTest {
        val serviceUuid = UUID.randomUUID()
        val characteristicUuid = UUID.randomUUID()
        val characteristic = mockCharacteristic(serviceUuid, characteristicUuid)
        val client: BleClient = BleClientImpl(
            context = mockPermittedContext(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.write(characteristic, byteArrayOf(0x01), timeout = 100.milliseconds)
            fail("Expected write to fail when no connection is active.")
        } catch (error: NotConnectedException) {
            assertEquals(
                emptyMap<BleCharacteristicId, Boolean>(),
                client.characteristicWriteStates.value,
            )
        }
    }

    @Test
    fun connectFailsWhenAndroidReturnsNullGatt() = runTest {
        val address = "01:02:03:04:05:06"
        val context = mockPermittedContext()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()

        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getRemoteDevice(address) } returns device
        every { device.connectGatt(context, false, any(), BluetoothDevice.TRANSPORT_LE) } returns null

        val client: BleClient = BleClientImpl(context = context, dispatcher = StandardTestDispatcher())
        try {
            client.connect(address = address, timeout = 1.seconds)
            fail("Expected null GATT failure.")
        } catch (error: BluetoothUnavailableException) {
            assertEquals("Android returned a null BluetoothGatt.", error.message)
        }
    }

    @Test
    fun connectDoesNothingWhenClientIsAlreadyConnected() = runTest {
        val address = "01:02:03:04:05:06"
        val context = mockPermittedContext()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val gatt = mockk<BluetoothGatt>()
        val callbackSlot = slot<BluetoothGattCallback>()

        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getRemoteDevice(address) } returns device
        every { device.name } returns "Sensor"
        every { device.address } returns address
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED
        every { device.connectGatt(context, false, capture(callbackSlot), BluetoothDevice.TRANSPORT_LE) } answers {
            callbackSlot.captured.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            gatt
        }

        val client: BleClient = BleClientImpl(context = context, dispatcher = StandardTestDispatcher())
        val firstConnect = client.connect(address = address, timeout = 1.seconds)
        val secondConnect = client.connect(address = "AA:BB:CC:DD:EE:FF", timeout = 1.seconds)

        assertEquals(firstConnect, secondConnect)
        assertTrue(client.connectionState.value is BluetoothConnectionStateConnected)
        verify(exactly = 1) { device.connectGatt(context, false, any(), BluetoothDevice.TRANSPORT_LE) }
    }

    @Test
    fun writePacketsAndObserveNotificationsRequiresAtLeastOnePacket() = runTest {
        val client: BleClient = BleClientImpl(
            context = mockPermittedContext(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.writePacketsAndObserveNotifications(
                characteristic = mockCharacteristic(UUID.randomUUID(), UUID.randomUUID()),
                packets = emptyList(),
            )
            fail("Expected empty packet collection to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("packets must contain at least one packet.", error.message)
        }
    }

    @Test
    fun writePacketsAndObserveNotificationsFailsWhenNotConnected() = runTest {
        val client: BleClient = BleClientImpl(
            context = mockPermittedContext(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.writePacketsAndObserveNotifications(
                characteristic = mockCharacteristic(UUID.randomUUID(), UUID.randomUUID()),
                packets = listOf(byteArrayOf(0x01)),
            ).toList()
            fail("Expected flow collection to fail when no connection is active.")
        } catch (error: NotConnectedException) {
            assertTrue(client.characteristicWriteStates.value.isEmpty())
        }
    }

    @Test
    fun readRssiFailsWhenNotConnected() = runTest {
        val client: BleClient = BleClientImpl(
            context = mockPermittedContext(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        try {
            client.readRssi(timeout = 100.milliseconds)
            fail("Expected RSSI read to fail when no connection is active.")
        } catch (error: NotConnectedException) {
            // Expected.
        }
    }

    @Test
    fun readRssiReturnsValueWhenGattCallbackReportsSuccess() = runTest {
        val address = "01:23:45:67:89:AB"
        val context = mockPermittedContext()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val gatt = mockk<BluetoothGatt>()
        val callbackSlot = slot<BluetoothGattCallback>()

        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getRemoteDevice(address) } returns device
        every { device.name } returns "Sensor"
        every { device.address } returns address
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED

        every { device.connectGatt(context, false, capture(callbackSlot), BluetoothDevice.TRANSPORT_LE) } answers {
            callbackSlot.captured.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_CONNECTED)
            gatt
        }
        every { gatt.readRemoteRssi() } answers {
            callbackSlot.captured.onReadRemoteRssi(gatt, -47, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val client: BleClient = BleClientImpl(
            context = context,
            dispatcher = StandardTestDispatcher(),
        )
        client.connect(address = address, timeout = 1.seconds)

        val rssi = client.readRssi(timeout = 1.seconds)
        assertEquals(-47, rssi)
    }

    @Test
    fun readRssiFailsWhenGattReadCannotStart() = runTest {
        val address = "AA:BB:CC:DD:EE:FF"
        val context = mockPermittedContext()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val gatt = mockk<BluetoothGatt>()
        val callbackSlot = slot<BluetoothGattCallback>()

        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getRemoteDevice(address) } returns device
        every { device.name } returns "Sensor"
        every { device.address } returns address
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED

        every { device.connectGatt(context, false, capture(callbackSlot), BluetoothDevice.TRANSPORT_LE) } answers {
            callbackSlot.captured.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, android.bluetooth.BluetoothProfile.STATE_CONNECTED)
            gatt
        }
        every { gatt.readRemoteRssi() } returns false

        val client: BleClient = BleClientImpl(
            context = context,
            dispatcher = StandardTestDispatcher(),
        )
        client.connect(address = address, timeout = 1.seconds)

        try {
            client.readRssi(timeout = 1.seconds)
            fail("Expected RSSI read start failure to throw.")
        } catch (error: BluetoothUnavailableException) {
            assertEquals("readRemoteRssi() returned false.", error.message)
        }
    }

    @Test
    fun readRssiThrowsGattExceptionWhenCallbackFails() = runTest {
        val address = "AA:BB:CC:DD:EE:11"
        val context = mockPermittedContext()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val gatt = mockk<BluetoothGatt>()
        val callbackSlot = slot<BluetoothGattCallback>()
        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getRemoteDevice(address) } returns device
        every { device.name } returns "Sensor"
        every { device.address } returns address
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED
        every { device.connectGatt(context, false, capture(callbackSlot), BluetoothDevice.TRANSPORT_LE) } answers {
            callbackSlot.captured.onConnectionStateChange(gatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED)
            gatt
        }
        every { gatt.readRemoteRssi() } answers {
            callbackSlot.captured.onReadRemoteRssi(gatt, -60, BluetoothGatt.GATT_FAILURE)
            true
        }

        val client: BleClient = BleClientImpl(context = context, dispatcher = StandardTestDispatcher())
        client.connect(address = address, timeout = 1.seconds)
        try {
            client.readRssi(timeout = 1.seconds)
            fail("Expected RSSI callback failure.")
        } catch (error: BluetoothGattException) {
            assertTrue(error.message.orEmpty().contains("Read remote RSSI"))
        }
    }

    @Test
    fun discoverServicesReturnsGattServicesWhenCallbackSucceeds() = runTest {
        val fixture = connectedFixture(address = "10:20:30:40:50:60")
        val discoveredService = mockk<BluetoothGattService>()
        every { fixture.gatt.services } returns listOf(discoveredService)
        every { fixture.gatt.discoverServices() } answers {
            fixture.callback.onServicesDiscovered(fixture.gatt, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val services = fixture.client.discoverServices(timeout = 1.seconds)

        assertEquals(listOf(discoveredService), services)
    }

    @Test
    fun discoverServicesThrowsGattExceptionWhenCallbackFails() = runTest {
        val fixture = connectedFixture(address = "10:20:30:40:50:99")
        every { fixture.gatt.discoverServices() } answers {
            fixture.callback.onServicesDiscovered(fixture.gatt, BluetoothGatt.GATT_FAILURE)
            true
        }

        try {
            fixture.client.discoverServices(timeout = 1.seconds)
            fail("Expected service discovery failure.")
        } catch (error: BluetoothGattException) {
            assertTrue(error.message.orEmpty().contains("Service discovery"))
        }
    }

    @Test
    fun writeReturnsWhenGattCallbackReportsSuccess() = runTest {
        val fixture = connectedFixture(address = "24:68:AC:EF:13:57")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        var writtenValue = byteArrayOf()
        var writtenType = -1
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            writtenValue = secondArg<ByteArray>()
            writtenType = thirdArg<Int>()
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        fixture.client.write(characteristic, byteArrayOf(0x12, 0x34), timeout = 1.seconds)
        assertArrayEquals(byteArrayOf(0x12, 0x34), writtenValue)
        assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, writtenType)
    }

    @Test
    fun writeThrowsGattExceptionWhenCallbackFails() = runTest {
        val fixture = connectedFixture(address = "24:68:AC:EF:13:99")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_FAILURE)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        try {
            fixture.client.write(characteristic, byteArrayOf(0x12), timeout = 1.seconds)
            fail("Expected write failure.")
        } catch (error: BluetoothGattException) {
            assertTrue(error.message.orEmpty().contains("Characteristic write"))
        }
    }

    @Test
    fun writePacketsAndObserveNotificationsEmitsResponseAfterFinalPacket() = runTest {
        val fixture = connectedFixture(address = "55:44:33:22:11:00")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)
        val writes = mutableListOf<ByteArray>()
        every { fixture.gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            val packet = secondArg<ByteArray>()
            characteristic.value = packet
            writes += characteristic.value.copyOf()
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            if (writes.size == 2) {
                fixture.callback.onCharacteristicChanged(fixture.gatt, characteristic, byteArrayOf(0x66))
            }
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        val response = async {
            fixture.client.writePacketsAndObserveNotifications(
                characteristic = characteristic,
                packets = listOf(byteArrayOf(0x01), byteArrayOf(0x02, 0x03)),
                operationTimeout = 1.seconds,
                responseTimeout = 1.seconds,
                disableNotificationsAfterResponse = false,
            ).first()
        }

        assertArrayEquals(byteArrayOf(0x66), response.await())
        assertEquals(2, writes.size)
        assertArrayEquals(byteArrayOf(0x01), writes[0])
        assertArrayEquals(byteArrayOf(0x02, 0x03), writes[1])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun writePacketsAndObserveNotificationsDisablesNotificationsAfterResponseByDefault() = runTest {
        val fixture = connectedFixture(address = "55:44:33:22:11:99", dispatcher = StandardTestDispatcher(testScheduler))
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)
        every { fixture.gatt.setCharacteristicNotification(characteristic, any()) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            fixture.callback.onCharacteristicChanged(fixture.gatt, characteristic, byteArrayOf(0x7F))
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        val result = fixture.client.writePacketsAndObserveNotifications(
            characteristic = characteristic,
            packets = listOf(byteArrayOf(0x01)),
            operationTimeout = 1.seconds,
            responseTimeout = 1.seconds,
        ).first()
        assertArrayEquals(byteArrayOf(0x7F), result)
        advanceUntilIdle()
        verify(atLeast = 1) { fixture.gatt.setCharacteristicNotification(characteristic, false) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun writePacketsAndObserveNotificationsTimesOutAfterLastPacketWhenNoResponse() = runTest {
        val fixture = connectedFixture(address = "AB:CD:EF:12:34:56", dispatcher = StandardTestDispatcher(testScheduler))
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)

        every { fixture.gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        try {
            fixture.client.writePacketsAndObserveNotifications(
                characteristic = characteristic,
                packets = listOf(byteArrayOf(0x01), byteArrayOf(0x02)),
                operationTimeout = 1.seconds,
                responseTimeout = 20.milliseconds,
                disableNotificationsAfterResponse = false,
            ).first()
            fail("Expected timeout when no notification is received after final packet.")
        } catch (error: BluetoothUnavailableException) {
            assertTrue(error.message.orEmpty().contains("Timed out waiting for a notification"))
        }
        advanceUntilIdle()
        fixture.client.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun notificationsByCharacteristicEmitsAndDisablesAfterCollectionStops() = runTest {
        val fixture = connectedFixture(address = "AA:BB:CC:DD:EE:10", dispatcher = StandardTestDispatcher(testScheduler))
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)

        every { fixture.gatt.setCharacteristicNotification(characteristic, any()) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val notification = async { fixture.client.notifications(characteristic).first() }
        advanceUntilIdle()
        fixture.callback.onCharacteristicChanged(fixture.gatt, characteristic, byteArrayOf(0x33, 0x44))

        assertArrayEquals(byteArrayOf(0x33, 0x44), notification.await())
        advanceUntilIdle()
        verify(atLeast = 1) { fixture.gatt.setCharacteristicNotification(characteristic, true) }
        verify(atLeast = 1) { fixture.gatt.writeDescriptor(descriptor, any()) }
        fixture.client.close()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("DEPRECATION")
    @Test
    fun notificationsByIdEmitsAndDisablesAfterCollectionStops() = runTest {
        val fixture = connectedFixture(address = "AA:BB:CC:DD:EE:20", dispatcher = StandardTestDispatcher(testScheduler))
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)
        val id = com.bledroid.core.BleCharacteristicId(service.uuid, characteristic.uuid)

        every { fixture.gatt.getService(service.uuid) } returns service
        every { fixture.gatt.setCharacteristicNotification(characteristic, any()) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val notification = async { fixture.client.notifications(id).first() }
        advanceUntilIdle()
        fixture.callback.onCharacteristicChanged(fixture.gatt, characteristic, byteArrayOf(0x55))

        assertArrayEquals(byteArrayOf(0x55), notification.await())
        advanceUntilIdle()
        verify(atLeast = 1) { fixture.gatt.setCharacteristicNotification(characteristic, true) }
        verify(atLeast = 1) { fixture.gatt.writeDescriptor(descriptor, any()) }
        fixture.client.close()
    }

    @Test
    fun readReturnsCharacteristicValueWhenGattCallbackSucceeds() = runTest {
        val fixture = connectedFixture(address = "12:34:56:78:90:AB")
        val characteristic = mockCharacteristic(UUID.randomUUID(), UUID.randomUUID())
        every { characteristic.value } returns byteArrayOf(0x01, 0x02, 0x03)
        every { fixture.gatt.readCharacteristic(characteristic) } answers {
            fixture.callback.onCharacteristicRead(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val value = fixture.client.read(characteristic, timeout = 1.seconds)

        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), value)
    }

    @Test
    fun readThrowsGattExceptionWhenCallbackFails() = runTest {
        val fixture = connectedFixture(address = "12:34:56:78:90:EE")
        val characteristic = mockCharacteristic(UUID.randomUUID(), UUID.randomUUID())
        every { fixture.gatt.readCharacteristic(characteristic) } answers {
            fixture.callback.onCharacteristicRead(fixture.gatt, characteristic, BluetoothGatt.GATT_FAILURE)
            true
        }

        try {
            fixture.client.read(characteristic, timeout = 1.seconds)
            fail("Expected read failure.")
        } catch (error: BluetoothGattException) {
            assertTrue(error.message.orEmpty().contains("Characteristic read"))
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedReadByServiceAndIdReturnValue() = runTest {
        val fixture = connectedFixture(address = "12:34:56:78:90:CD")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        characteristic.value = byteArrayOf(0x41, 0x42)
        service.addCharacteristic(characteristic)
        every { fixture.gatt.getService(service.uuid) } returns service
        every { fixture.gatt.readCharacteristic(characteristic) } answers {
            fixture.callback.onCharacteristicRead(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val byService = fixture.client.read(service, characteristic.uuid, timeout = 1.seconds)
        val byId = fixture.client.read(BleCharacteristicId(service.uuid, characteristic.uuid), timeout = 1.seconds)

        assertArrayEquals(byteArrayOf(0x41, 0x42), byService)
        assertArrayEquals(byteArrayOf(0x41, 0x42), byId)
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedWriteByServiceAndIdDelegatesToGatt() = runTest {
        val fixture = connectedFixture(address = "22:33:44:55:66:77")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        every { fixture.gatt.getService(service.uuid) } returns service
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        fixture.client.write(service, characteristic.uuid, byteArrayOf(0x01), timeout = 1.seconds)
        fixture.client.write(BleCharacteristicId(service.uuid, characteristic.uuid), byteArrayOf(0x02), timeout = 1.seconds)
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedEnableNotificationsByServiceAndIdEnablePath() = runTest {
        val fixture = connectedFixture(address = "88:77:66:55:44:33")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)

        every { fixture.gatt.getService(service.uuid) } returns service
        every { fixture.gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }

        fixture.client.enableNotifications(service, characteristic.uuid, enabled = true, timeout = 1.seconds)
        fixture.client.enableNotifications(BleCharacteristicId(service.uuid, characteristic.uuid), enabled = true, timeout = 1.seconds)
    }

    @Suppress("DEPRECATION")
    @Test
    fun notificationsByServiceEmitsValue() = runTest {
        val fixture = connectedFixture(address = "33:44:55:66:77:88", dispatcher = StandardTestDispatcher(testScheduler))
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)
        every { fixture.gatt.setCharacteristicNotification(characteristic, any()) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }

        val deferred = async { fixture.client.notifications(service, characteristic.uuid).first() }
        advanceUntilIdle()
        fixture.callback.onCharacteristicChanged(fixture.gatt, characteristic, byteArrayOf(0x09))
        assertArrayEquals(byteArrayOf(0x09), deferred.await())
        fixture.client.close()
    }

    @Test
    fun enableNotificationsFailsWhenCccdDescriptorIsMissing() = runTest {
        val fixture = connectedFixture(address = "99:88:77:66:55:44")
        val characteristic = mockCharacteristic(UUID.randomUUID(), UUID.randomUUID())
        every { characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) } returns null

        try {
            fixture.client.enableNotifications(characteristic, enabled = true, timeout = 1.seconds)
            fail("Expected missing CCCD descriptor to fail.")
        } catch (error: BluetoothUnavailableException) {
            assertEquals(
                "Client characteristic config descriptor 0x2902 was not found.",
                error.message,
            )
        }
    }

    @Test
    fun enableNotificationsFailsWhenLocalNotificationEnableReturnsFalse() = runTest {
        val fixture = connectedFixture(address = "99:88:77:66:55:45")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)
        every { fixture.gatt.setCharacteristicNotification(characteristic, true) } returns false

        try {
            fixture.client.enableNotifications(characteristic, enabled = true, timeout = 1.seconds)
            fail("Expected local notification enable failure.")
        } catch (error: BluetoothUnavailableException) {
            assertEquals("setCharacteristicNotification() returned false.", error.message)
        }
    }

    @Test
    fun disconnectMarksStateDisconnectingAndDelegatesToGatt() = runTest {
        val fixture = connectedFixture(address = "FE:DC:BA:98:76:54")
        every { fixture.gatt.disconnect() } answers {}
        every { fixture.gatt.close() } answers {}

        fixture.client.disconnect()

        assertEquals(BluetoothConnectionStateDisconnecting, fixture.client.connectionState.value)
        verify(exactly = 1) { fixture.gatt.disconnect() }
    }

    @Test
    fun disconnectWithoutConnectionLeavesStateDisconnected() = runTest {
        val client: BleClient = BleClientImpl(
            context = mockPermittedContext(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        client.disconnect()
        assertEquals(BluetoothConnectionStateDisconnected, client.connectionState.value)
    }

    @Test
    fun disconnectFailureFallsBackToCloseGatt() = runTest {
        val fixture = connectedFixture(address = "FE:DC:BA:11:22:33")
        every { fixture.gatt.disconnect() } throws RuntimeException("disconnect failure")
        every { fixture.gatt.close() } answers {}

        fixture.client.disconnect()

        verify(atLeast = 1) { fixture.gatt.disconnect() }
        verify(exactly = 1) { fixture.gatt.close() }
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedReadByIdFailsWhenServiceIsMissing() = runTest {
        val fixture = connectedFixture(address = "01:01:01:01:01:01")
        val id = BleCharacteristicId(UUID.randomUUID(), UUID.randomUUID())
        every { fixture.gatt.getService(id.serviceUuid) } returns null

        try {
            fixture.client.read(id, timeout = 1.seconds)
            fail("Expected missing service.")
        } catch (error: BluetoothUnavailableException) {
            assertTrue(error.message.orEmpty().contains("was not found"))
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedReadByServiceFailsWhenCharacteristicMissing() = runTest {
        val fixture = connectedFixture(address = "02:02:02:02:02:02")
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val missingCharacteristicUuid = UUID.randomUUID()

        try {
            fixture.client.read(service, missingCharacteristicUuid, timeout = 1.seconds)
            fail("Expected missing characteristic.")
        } catch (error: BluetoothUnavailableException) {
            assertTrue(error.message.orEmpty().contains("was not found"))
        }
    }

    @Test
    fun notificationsByCharacteristicFailsWhenCharacteristicHasNoService() = runTest {
        val fixture = connectedFixture(address = "03:03:03:03:03:03")
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )

        try {
            fixture.client.notifications(characteristic).first()
            fail("Expected detached characteristic failure.")
        } catch (error: BluetoothUnavailableException) {
            assertTrue(error.message.orEmpty().contains("is not attached to a service"))
        }
    }

    @Test
    fun writeAndObserveNotificationsEmitsResponse() = runTest {
        val fixture = connectedFixture(address = "04:04:04:04:04:04", dispatcher = StandardTestDispatcher(testScheduler))
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        characteristic.addDescriptor(descriptor)

        every { fixture.gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { fixture.gatt.writeDescriptor(descriptor, any()) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }
        every { fixture.gatt.writeDescriptor(descriptor) } answers {
            fixture.callback.onDescriptorWrite(fixture.gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
            true
        }
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            fixture.callback.onCharacteristicChanged(fixture.gatt, characteristic, byteArrayOf(0x7F))
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        val response = fixture.client.writeAndObserveNotifications(
            characteristic = characteristic,
            value = byteArrayOf(0x01, 0x02),
            operationTimeout = 1.seconds,
            responseTimeout = 1.seconds,
            disableNotificationsAfterResponse = false,
        ).first()

        assertArrayEquals(byteArrayOf(0x7F), response)
    }

    @Test
    fun queuedWriteTimeoutDoesNotStartUntilThatWriteBeginsProcessing() = runTest {
        val fixture = connectedFixture(
            address = "05:05:05:05:05:05",
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val service = BluetoothGattService(UUID.randomUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            UUID.randomUUID(),
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(characteristic)

        var writeCallCount = 0
        every { fixture.gatt.writeCharacteristic(characteristic, any(), any()) } answers {
            writeCallCount += 1
            if (writeCallCount == 2) {
                fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
            }
            android.bluetooth.BluetoothStatusCodes.SUCCESS
        }

        val firstWrite = async {
            fixture.client.write(
                characteristic = characteristic,
                value = byteArrayOf(0x01),
                timeout = 5.seconds,
            )
        }
        runCurrent()

        val secondWrite = async {
            fixture.client.write(
                characteristic = characteristic,
                value = byteArrayOf(0x02),
                timeout = 100.milliseconds,
            )
        }

        advanceTimeBy(500)
        assertFalse(secondWrite.isCompleted)

        fixture.callback.onCharacteristicWrite(fixture.gatt, characteristic, BluetoothGatt.GATT_SUCCESS)
        runCurrent()

        firstWrite.await()
        secondWrite.await()
        assertEquals(2, writeCallCount)
    }

    private fun mockPermittedContext(): Context {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_GRANTED
        return context
    }

    private fun mockDeniedContext(): Context {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_DENIED
        return context
    }

    private data class ConnectedFixture(
        val client: BleClient,
        val gatt: BluetoothGatt,
        val callback: BluetoothGattCallback,
    )

    private suspend fun connectedFixture(
        address: String,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(),
    ): ConnectedFixture {
        val context = mockPermittedContext()
        val manager = mockk<BluetoothManager>()
        val adapter = mockk<BluetoothAdapter>()
        val device = mockk<BluetoothDevice>()
        val gatt = mockk<BluetoothGatt>()
        val callbackSlot = slot<BluetoothGattCallback>()

        every { context.getSystemService(BluetoothManager::class.java) } returns manager
        every { manager.adapter } returns adapter
        every { adapter.isEnabled } returns true
        every { adapter.getRemoteDevice(address) } returns device
        every { device.name } returns "Sensor"
        every { device.address } returns address
        every { device.type } returns BluetoothDevice.DEVICE_TYPE_LE
        every { device.bondState } returns BluetoothDevice.BOND_BONDED

        every { device.connectGatt(context, false, capture(callbackSlot), BluetoothDevice.TRANSPORT_LE) } answers {
            callbackSlot.captured.onConnectionStateChange(
                gatt,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_CONNECTED,
            )
            gatt
        }

        val client: BleClient = BleClientImpl(
            context = context,
            dispatcher = dispatcher,
        )
        client.connect(address = address, timeout = 1.seconds)
        return ConnectedFixture(client = client, gatt = gatt, callback = callbackSlot.captured)
    }

    private fun mockCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
    ): BluetoothGattCharacteristic {
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { service.uuid } returns serviceUuid
        every { characteristic.service } returns service
        every { characteristic.uuid } returns characteristicUuid
        return characteristic
    }
}
