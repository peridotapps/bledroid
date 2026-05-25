package com.bledroid.core

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothEventMonitorRobolectricTest {
    @Test
    fun eventsEmitsAdapterStateChangedFromSystemBroadcast() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = BluetoothEventMonitorImpl(context)

        val awaitEvent = async { withTimeout(2_000) { monitor.events().first() } }
        val receiver = registerAndFindReceiver(this, monitor, context)
        receiver.onReceive(
            context,
            Intent(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
                putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON)
                putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_TURNING_ON)
            },
        )

        val event = awaitEvent.await()
        assertEquals(
            BluetoothBroadcastEvent.AdapterStateChanged(
                state = BluetoothAdapterPowerState.On,
                previousState = BluetoothAdapterPowerState.TurningOn,
            ),
            event,
        )
    }

    @Test
    fun eventsEmitsDiscoveryStartedAndFinishedFromBroadcasts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = BluetoothEventMonitorImpl(context)

        val events = async {
            withTimeout(2_000) {
                monitor.events().take(2).toList()
            }
        }
        val receiver = registerAndFindReceiver(this, monitor, context)
        receiver.onReceive(context, Intent(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        receiver.onReceive(context, Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        assertEquals(
            listOf(
                BluetoothBroadcastEvent.DiscoveryStarted,
                BluetoothBroadcastEvent.DiscoveryFinished,
            ),
            events.await(),
        )
    }

    @Test
    fun eventsEmitsNameChangedFromLocalNameBroadcast() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = BluetoothEventMonitorImpl(context)

        val awaitEvent = async { withTimeout(2_000) { monitor.events().first { it is BluetoothBroadcastEvent.NameChanged } } }
        val receiver = registerAndFindReceiver(this, monitor, context)
        receiver.onReceive(
            context,
            Intent(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED).apply {
                putExtra(BluetoothAdapter.EXTRA_LOCAL_NAME, "Desk Adapter")
            },
        )

        assertEquals(BluetoothBroadcastEvent.NameChanged("Desk Adapter"), awaitEvent.await())
    }

    @Test
    fun eventsEmitsScanModeChangedFromSystemBroadcast() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = BluetoothEventMonitorImpl(context)

        val awaitEvent = async { withTimeout(2_000) { monitor.events().first { it is BluetoothBroadcastEvent.ScanModeChanged } } }
        val receiver = registerAndFindReceiver(this, monitor, context)
        receiver.onReceive(
            context,
            Intent(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED).apply {
                putExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                putExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, BluetoothAdapter.SCAN_MODE_CONNECTABLE)
            },
        )

        assertEquals(
            BluetoothBroadcastEvent.ScanModeChanged(
                mode = BluetoothScanMode.ConnectableDiscoverable,
                previousMode = BluetoothScanMode.Connectable,
            ),
            awaitEvent.await(),
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun registerAndFindReceiver(
        scope: CoroutineScope,
        monitor: BluetoothEventMonitor,
        context: Context,
    ): BroadcastReceiver {
        val collector = scope.async { monitor.events().first() }
        val app = context as Application
        withTimeout(2_000) {
            while (shadowOf(app).registeredReceivers.isEmpty()) {
                yield()
            }
        }
        collector.cancel()
        val receiver = shadowOf(app).registeredReceivers.firstOrNull()?.broadcastReceiver
        assertNotNull(receiver)
        return receiver!!
    }
}
