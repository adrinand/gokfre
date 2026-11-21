package com.adrinand.gokfre.core.hotkey

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test

@Suppress("TooManyFunctions")
class MacCarbonHotkeyProviderTest {
    private val binding = FakeCarbonHotkeyBinding()

    @Test
    fun `constructor installs the event handler`() {
        MacCarbonHotkeyProvider(binding)

        binding.installedHandlers shouldBe 1
    }

    @Test
    fun `handler install failure throws`() {
        binding.installStatus = -1

        val result = runCatching { MacCarbonHotkeyProvider(binding) }

        result.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
    }

    @Test
    fun `register forwards the mapped hotkey`() {
        val provider = MacCarbonHotkeyProvider(binding)

        provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))) {}

        binding.registeredHotkeys shouldBe listOf(CarbonHotkey(40, 0x0100))
    }

    @Test
    fun `hotkey press invokes the registered callback`() {
        val provider = MacCarbonHotkeyProvider(binding)
        var invoked = 0
        provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))) { invoked++ }

        binding.press(binding.registeredIds.single())

        invoked shouldBe 1
    }

    @Test
    fun `unregister stops the callback`() {
        val provider = MacCarbonHotkeyProvider(binding)
        val hotkey = Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))
        var invoked = 0
        provider.register(hotkey) { invoked++ }

        provider.unregister(hotkey)
        binding.press(binding.registeredIds.single())

        invoked shouldBe 0
        binding.unregisteredIds shouldBe listOf(binding.registeredIds.single())
    }

    @Test
    fun `conflicting hotkey throws`() {
        val provider = MacCarbonHotkeyProvider(binding)
        binding.registerStatus = -9878

        val result =
            runCatching {
                provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))) {}
            }

        result.exceptionOrNull()?.message shouldContain "already used by another app or macOS"
    }

    @Test
    fun `unsupported key throws`() {
        val provider = MacCarbonHotkeyProvider(binding)

        val result = runCatching { provider.register(Hotkey(0x9999, setOf(ModifierKey.SUPER))) {} }

        result.exceptionOrNull()?.message shouldContain "Unsupported key"
    }

    @Test
    fun `shift only hotkey throws`() {
        val provider = MacCarbonHotkeyProvider(binding)

        val result = runCatching { provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SHIFT))) {} }

        result.exceptionOrNull()?.message shouldContain "must include Ctrl, Alt, or Super"
    }

    @Test
    fun `modifier-less hotkey throws`() {
        val provider = MacCarbonHotkeyProvider(binding)

        val result = runCatching { provider.register(Hotkey(KeyCode.VC_K)) {} }

        result.exceptionOrNull()?.message shouldContain "must include Ctrl, Alt, or Super"
    }

    @Test
    fun `duplicate register releases the previous hotkey`() {
        val provider = MacCarbonHotkeyProvider(binding)
        val hotkey = Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))
        provider.register(hotkey) {}
        var invoked = 0

        provider.register(hotkey) { invoked++ }

        val firstId = binding.registeredIds[0]
        val secondId = binding.registeredIds[1]
        binding.unregisteredIds shouldBe listOf(firstId)
        binding.press(firstId)
        invoked shouldBe 0
        binding.press(secondId)
        invoked shouldBe 1
    }

    @Test
    fun `failed unregister keeps the callback alive`() {
        val provider = MacCarbonHotkeyProvider(binding)
        val hotkey = Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))
        var invoked = 0
        provider.register(hotkey) { invoked++ }
        binding.unregisterStatus = -1

        provider.unregister(hotkey)
        binding.press(binding.registeredIds.single())

        invoked shouldBe 1
    }

    @Test
    fun `dispose after a failed registration removes the handler`() {
        val provider = MacCarbonHotkeyProvider(binding)
        binding.registerStatus = -9878
        runCatching { provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))) {} }

        provider.dispose()

        binding.handlerRemoved shouldBe true
    }

    @Test
    fun `dispose is idempotent`() {
        val provider = MacCarbonHotkeyProvider(binding)

        provider.dispose()
        provider.dispose()

        binding.handlerRemoved shouldBe true
    }

    @Test
    fun `dispose retries when unregister fails`() {
        val provider = MacCarbonHotkeyProvider(binding)
        provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))) {}
        binding.unregisterStatus = -1

        provider.dispose()
        binding.handlerRemoved shouldBe false

        binding.unregisterStatus = 0
        provider.dispose()
        binding.handlerRemoved shouldBe true
    }

    @Test
    fun `dispose retries when handler removal fails`() {
        val provider = MacCarbonHotkeyProvider(binding)
        binding.removeHandlerStatus = -1

        provider.dispose()
        binding.removeHandlerCalls shouldBe 1

        binding.removeHandlerStatus = 0
        provider.dispose()
        binding.removeHandlerCalls shouldBe 2
    }

    @Test
    fun `dispose unregisters every hotkey and removes the handler`() {
        val provider = MacCarbonHotkeyProvider(binding)
        provider.register(Hotkey(KeyCode.VC_K, setOf(ModifierKey.SUPER))) {}
        provider.register(Hotkey(KeyCode.VC_A, setOf(ModifierKey.CTRL))) {}

        provider.dispose()

        binding.unregisteredIds.size shouldBe 2
        binding.handlerRemoved shouldBe true
    }
}

private class FakeCarbonHotkeyBinding : CarbonHotkeyBinding {
    var installStatus = 0
    var registerStatus = 0
    var unregisterStatus = 0
    var removeHandlerStatus = 0
    var installedHandlers = 0
    var handlerRemoved = false
    var removeHandlerCalls = 0
    val registeredHotkeys = mutableListOf<CarbonHotkey>()
    val registeredIds = mutableListOf<Int>()
    val unregisteredIds = mutableListOf<Int>()
    private var onHotkeyPressed: ((Int) -> Unit)? = null

    override fun installHandler(onHotkeyPressed: (Int) -> Unit): Int {
        installedHandlers++
        this.onHotkeyPressed = onHotkeyPressed
        return installStatus
    }

    override fun register(
        hotkey: CarbonHotkey,
        id: Int,
    ): Int {
        if (registerStatus != 0) {
            return registerStatus
        }
        registeredHotkeys += hotkey
        registeredIds += id
        return 0
    }

    override fun unregister(id: Int): Int {
        unregisteredIds += id
        return unregisterStatus
    }

    override fun removeHandler(): Int {
        removeHandlerCalls++
        handlerRemoved = true
        return removeHandlerStatus
    }

    fun press(id: Int) {
        onHotkeyPressed?.invoke(id)
    }
}
