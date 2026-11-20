package com.adrinand.gokfre.core.hotkey

import com.sun.jna.Pointer
import com.sun.jna.platform.mac.Carbon
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

private val logger = Logger.getLogger("com.adrinand.gokfre.core.hotkey.MacCarbonHotkeyProvider")

private const val EVENT_HOT_KEY_EXISTS_ERR = -9878
private const val PARAM_ERR = -50

internal interface CarbonHotkeyBinding {
    fun installHandler(onHotkeyPressed: (Int) -> Unit): Int

    fun register(
        hotkey: CarbonHotkey,
        id: Int,
    ): Int

    fun unregister(id: Int): Int

    fun removeHandler(): Int
}

internal class MacCarbonHotkeyProvider(
    private val binding: CarbonHotkeyBinding = JnaCarbonHotkeyBinding(),
) : GlobalHotkeyProvider {
    private val callbacks = ConcurrentHashMap<Int, () -> Unit>()
    private val registeredIds = ConcurrentHashMap<Hotkey, Int>()
    private var handlerInstalled = false

    init {
        val status = binding.installHandler(::dispatch)
        check(status == 0) { "Failed to install macOS hotkey event handler (status $status)" }
        handlerInstalled = true
    }

    override fun register(
        hotkey: Hotkey,
        callback: () -> Unit,
    ) {
        check(hotkey.hasNonShiftModifier()) {
            "Hotkey ${hotkey.toDisplayString()} must include Ctrl, Alt, or Super"
        }
        val carbonHotkey =
            hotkey.toCarbonHotkey()
                ?: error("Unsupported key for macOS global hotkey: ${hotkey.toDisplayString()}")
        unregisterExisting(hotkey)
        val id = nextId.getAndIncrement()
        val status = binding.register(carbonHotkey, id)
        if (status != 0) {
            val reason =
                if (status == EVENT_HOT_KEY_EXISTS_ERR) {
                    "already used by another app or macOS"
                } else {
                    "status $status"
                }
            error("Failed to register hotkey ${hotkey.toDisplayString()}: $reason")
        }
        registeredIds[hotkey] = id
        callbacks[id] = callback
        logger.info { "Registered macOS hotkey ${hotkey.toDisplayString()} (id=$id)" }
    }

    override fun unregister(hotkey: Hotkey) {
        val id = registeredIds[hotkey] ?: return
        val status = binding.unregister(id)
        if (status != 0) {
            logger.warning { "Failed to unregister macOS hotkey ${hotkey.toDisplayString()} (status $status)" }
            return
        }
        registeredIds.remove(hotkey, id)
        callbacks.remove(id)
    }

    override fun dispose() {
        var teardownFailed = false
        registeredIds.values.forEach { id ->
            val status = binding.unregister(id)
            if (status == 0) {
                callbacks.remove(id)
            } else {
                teardownFailed = true
                logger.warning { "Failed to unregister macOS hotkey id=$id (status $status)" }
            }
        }
        if (teardownFailed) {
            retainBinding()
            return
        }
        registeredIds.clear()
        callbacks.clear()
        if (handlerInstalled) {
            val status = binding.removeHandler()
            if (status == 0) {
                handlerInstalled = false
            } else {
                logger.warning { "Failed to remove macOS hotkey event handler (status $status)" }
                retainBinding()
            }
        }
    }

    private fun unregisterExisting(hotkey: Hotkey) {
        val existingId = registeredIds[hotkey] ?: return
        val status = binding.unregister(existingId)
        if (status != 0) {
            error("Failed to replace hotkey ${hotkey.toDisplayString()} (status $status)")
        }
        registeredIds.remove(hotkey, existingId)
        callbacks.remove(existingId)
    }

    private fun dispatch(id: Int) {
        callbacks[id]?.invoke()
    }

    private fun retainBinding() {
        if (!retainedBindings.contains(binding)) {
            retainedBindings.add(binding)
        }
        logger.warning { "Retaining macOS hotkey binding because native teardown failed" }
    }

    private companion object {
        private val nextId = AtomicInteger(1)
        private val retainedBindings = CopyOnWriteArrayList<CarbonHotkeyBinding>()
    }
}

private class JnaCarbonHotkeyBinding : CarbonHotkeyBinding {
    private val hotKeyRefs = ConcurrentHashMap<Int, Pointer>()
    private val handlerRef = PointerByReference()

    // Keeps the JNA callback strongly reachable; the native side only holds a raw pointer.
    private var eventHandler: Carbon.EventHandlerProcPtr? = null

    override fun installHandler(onHotkeyPressed: (Int) -> Unit): Int {
        val handler =
            object : Carbon.EventHandlerProcPtr {
                override fun callback(
                    inHandlerCallRef: Pointer?,
                    inEvent: Pointer?,
                    inUserData: Pointer?,
                ): Int {
                    val eventHotKeyId = Carbon.EventHotKeyID()
                    val status =
                        Carbon.INSTANCE.GetEventParameter(
                            inEvent,
                            K_EVENT_PARAM_DIRECT_OBJECT,
                            TYPE_EVENT_HOT_KEY_ID,
                            null,
                            eventHotKeyId.size(),
                            null,
                            eventHotKeyId,
                        )
                    if (status == 0 && eventHotKeyId.signature == HOTKEY_SIGNATURE) {
                        runCatching { onHotkeyPressed(eventHotKeyId.id) }
                            .onFailure { logger.warning { "macOS hotkey callback failed: ${it.message}" } }
                    } else if (status != 0) {
                        logger.warning { "Failed to read macOS hotkey event parameter (status $status)" }
                    }
                    return 0
                }
            }
        eventHandler = handler

        val eventType = Carbon.EventTypeSpec()
        eventType.eventClass = K_EVENT_CLASS_KEYBOARD
        eventType.eventKind = K_EVENT_HOT_KEY_PRESSED
        val eventTypes = arrayOf(eventType)
        return Carbon.INSTANCE.InstallEventHandler(
            Carbon.INSTANCE.GetEventDispatcherTarget(),
            handler,
            eventTypes.size,
            eventTypes,
            null,
            handlerRef,
        )
    }

    override fun register(
        hotkey: CarbonHotkey,
        id: Int,
    ): Int {
        val hotKeyId = Carbon.EventHotKeyID.ByValue()
        hotKeyId.signature = HOTKEY_SIGNATURE
        hotKeyId.id = id
        val hotKeyRef = PointerByReference()
        val status =
            Carbon.INSTANCE.RegisterEventHotKey(
                hotkey.keyCode,
                hotkey.modifiers,
                hotKeyId,
                Carbon.INSTANCE.GetEventDispatcherTarget(),
                0,
                hotKeyRef,
            )
        if (status != 0) {
            return status
        }
        val ref = hotKeyRef.value ?: return PARAM_ERR
        hotKeyRefs[id] = ref
        return 0
    }

    override fun unregister(id: Int): Int {
        val ref = hotKeyRefs[id] ?: return 0
        val status = Carbon.INSTANCE.UnregisterEventHotKey(ref)
        if (status == 0) {
            hotKeyRefs.remove(id)
        }
        return status
    }

    override fun removeHandler(): Int {
        if (eventHandler == null) {
            return 0
        }
        val ref = handlerRef.value ?: return 0
        val status = Carbon.INSTANCE.RemoveEventHandler(ref)
        if (status == 0) {
            eventHandler = null
            handlerRef.value = null
        }
        return status
    }

    private companion object {
        private const val K_EVENT_CLASS_KEYBOARD = 0x6B657962 // 'keyb'
        private const val K_EVENT_HOT_KEY_PRESSED = 5
        private const val K_EVENT_PARAM_DIRECT_OBJECT = 0x2D2D2D2D // '----'
        private const val TYPE_EVENT_HOT_KEY_ID = 0x686B6964 // 'hkid'
        private const val HOTKEY_SIGNATURE = 0x474F4B46 // 'GOKF'
    }
}
