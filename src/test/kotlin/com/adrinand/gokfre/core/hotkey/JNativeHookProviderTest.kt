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

@Suppress("TooManyFunctions")
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
    fun `left meta modifier alone should invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.META_L_MASK,
        )

        invoked.get() shouldBe true
    }

    @Test
    fun `right meta modifier alone should invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.META_R_MASK,
        )

        invoked.get() shouldBe true
    }

    @Test
    fun `no modifiers should not invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = 0,
        )

        invoked.get() shouldBe false
    }

    @Test
    fun `different key with meta should not invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_J,
            modifiers = NativeKeyEvent.META_L_MASK,
        )

        invoked.get() shouldBe false
    }

    @Test
    fun `extra modifier should not invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        provider!!.register(Hotkey.DEFAULT_TOGGLE) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.META_L_MASK or NativeKeyEvent.CTRL_L_MASK,
        )

        invoked.get() shouldBe false
    }

    @Test
    fun `missing modifier should not invoke callback`() {
        assumeProviderAvailable()
        val invoked = AtomicBoolean(false)

        val hotkey =
            Hotkey(
                keyCode = NativeKeyEvent.VC_K,
                modifiers = setOf(ModifierKey.SHIFT, ModifierKey.SUPER),
            )
        provider!!.register(hotkey) { invoked.set(true) }
        simulateNativeKeyPress(
            keyCode = NativeKeyEvent.VC_K,
            modifiers = NativeKeyEvent.META_L_MASK,
        )

        invoked.get() shouldBe false
        provider!!.unregister(hotkey)
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
