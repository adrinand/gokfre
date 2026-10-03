package com.adrinand.gokfre.core.hotkey

import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicBoolean

class JNativeHookProviderTest {
    @After
    fun tearDown() {
        provider?.unregister(Hotkey.DEFAULT_TOGGLE)
    }

    @Test
    fun `default Super plus K hotkey should invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.META_MASK,
        )

        invoked.get() shouldBe true
    }

    @Test
    fun `wrong modifiers should not invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.CTRL_MASK,
        )

        invoked.get() shouldBe false
    }

    @Test
    fun `unregister should stop callback invocation`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        provider!!.unregister(Hotkey.DEFAULT_TOGGLE)
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.META_MASK,
        )

        invoked.get() shouldBe false
    }

    private fun assumeProviderAvailable() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless())
        Assume.assumeNotNull(provider)
    }

    private fun simulateNativeKeyPress(
        keyCode: Int,
        modifiers: Int,
    ) {
        val event =
            NativeKeyEvent(
                NativeKeyEvent.NATIVE_KEY_PRESSED,
                modifiers,
                0,
                keyCode,
                NativeKeyEvent.CHAR_UNDEFINED,
            )
        val listenerField = JNativeHookProvider::class.java.getDeclaredField("listener")
        listenerField.isAccessible = true
        val listener = listenerField.get(provider) as NativeKeyListener
        listener.nativeKeyPressed(event)
    }

    companion object {
        private var provider: JNativeHookProvider? = null

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            provider = tryCreateProvider()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            provider?.dispose()
            provider = null
        }

        @JvmStatic
        private fun tryCreateProvider(): JNativeHookProvider? =
            try {
                JNativeHookProvider()
            } catch (e: UnsatisfiedLinkError) {
                Assume.assumeNoException("Native library is not available", e)
                null
            } catch (e: NativeHookException) {
                Assume.assumeNoException("Native hook is not available", e)
                null
            }
    }
}
