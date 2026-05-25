package com.bledroid.companion

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import com.bledroid.core.BluetoothUnavailableException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleCompanionDeviceManagerTest {
    @Test
    fun isAvailableFalseWhenCompanionManagerMissing() {
        val context = mockk<Context>()
        every { context.getSystemService(CompanionDeviceManager::class.java) } returns null

        val manager = BleCompanionDeviceManagerImpl(context)

        assertFalse(manager.isAvailable())
    }

    @Test
    fun isAvailableTrueWhenCompanionManagerPresent() {
        val context = mockk<Context>()
        every { context.getSystemService(CompanionDeviceManager::class.java) } returns mockk()

        val manager = BleCompanionDeviceManagerImpl(context)

        assertTrue(manager.isAvailable())
    }

    @Test
    fun requestAssociationReturnsIntentSenderFromCallback() = runTest {
        val context = mockk<Context>()
        val systemManager = mockk<CompanionDeviceManager>()
        val callbackSlot = slot<CompanionDeviceManager.Callback>()
        val request = AssociationRequest.Builder().setSingleDevice(true).build()
        val intentSender = mockk<IntentSender>()

        every { context.getSystemService(CompanionDeviceManager::class.java) } returns systemManager
        every { systemManager.associate(request, any<Executor>(), capture(callbackSlot)) } answers {
            callbackSlot.captured.onAssociationPending(intentSender)
        }

        val manager = BleCompanionDeviceManagerImpl(context)
        val result = manager.requestAssociation(request, timeout = 2.seconds)

        assertSame(intentSender, result)
    }

    @Test
    fun requestAssociationThrowsWhenCallbackFails() = runTest {
        val context = mockk<Context>()
        val systemManager = mockk<CompanionDeviceManager>()
        val callbackSlot = slot<CompanionDeviceManager.Callback>()
        val request = AssociationRequest.Builder().setSingleDevice(true).build()

        every { context.getSystemService(CompanionDeviceManager::class.java) } returns systemManager
        every { systemManager.associate(request, any<Executor>(), capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure("association failed")
        }

        val manager = BleCompanionDeviceManagerImpl(context)

        try {
            manager.requestAssociation(request, timeout = 2.seconds)
            fail("Expected association failure.")
        } catch (error: BluetoothUnavailableException) {
            assertTrue(error.message.orEmpty().contains("association failed"))
        }
    }
}
