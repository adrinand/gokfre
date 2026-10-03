package com.adrinand.gokfre.core.hotkey

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.logging.Logger

interface GokfreHotkeyNative : Library {
    @Suppress("FunctionName")
    fun gokfre_hotkey_init(): Pointer

    @Suppress("FunctionName")
    fun gokfre_hotkey_register(
        manager: Pointer,
        combo: String,
        callback: GokfreHotkeyCallback,
    ): Int

    @Suppress("FunctionName")
    fun gokfre_hotkey_shutdown(manager: Pointer)

    interface GokfreHotkeyCallback : Callback {
        fun invoke()
    }

    companion object {
        private const val LIB_NAME = "gokfre_hotkey"
        private val logger = Logger.getLogger("com.adrinand.gokfre.core.hotkey.GokfreHotkeyNative")

        val INSTANCE: GokfreHotkeyNative by lazy {
            loadWithFallback()
        }

        @Suppress("ReturnCount")
        private fun loadWithFallback(): GokfreHotkeyNative {
            try {
                return Native.load(LIB_NAME, GokfreHotkeyNative::class.java)
            } catch (firstError: UnsatisfiedLinkError) {
                logger.fine { "Native.load failed, trying jar extraction: ${firstError.message}" }
                val extracted = extractFromJar()
                if (extracted != null) {
                    try {
                        // Try loading via absolute path
                        return Native.load(extracted.absolutePath, GokfreHotkeyNative::class.java)
                    } catch (e: UnsatisfiedLinkError) {
                        logger.fine { "Loading extracted lib via Native.load(path) failed: ${e.message}" }
                    }
                    try {
                        NativeLibrary.addSearchPath(LIB_NAME, extracted.parentFile.absolutePath)
                        return Native.load(LIB_NAME, GokfreHotkeyNative::class.java)
                    } catch (e: UnsatisfiedLinkError) {
                        logger.fine { "Loading via addSearchPath failed: ${e.message}" }
                    }
                    try {
                        System.load(extracted.absolutePath)
                        return Native.load(LIB_NAME, GokfreHotkeyNative::class.java)
                    } catch (e: UnsatisfiedLinkError) {
                        logger.fine { "Loading via System.load failed: ${e.message}" }
                    }
                }
                throw firstError
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun extractFromJar(): File? {
            val candidates =
                listOf(
                    "/linux-x86-64/libgokfre_hotkey.so",
                    "/linux-x86_64/libgokfre_hotkey.so",
                    "/linux-aarch64/libgokfre_hotkey.so",
                    "/natives/linux-x86-64/libgokfre_hotkey.so",
                    "/natives/linux-aarch64/libgokfre_hotkey.so",
                )
            for (path in candidates) {
                val stream = GokfreHotkeyNative::class.java.getResourceAsStream(path) ?: continue
                try {
                    val temp = File.createTempFile("libgokfre_hotkey-", ".so")
                    temp.deleteOnExit()
                    FileOutputStream(temp).use { out ->
                        stream.copyTo(out)
                    }
                    temp.setExecutable(true)
                    logger.fine { "Extracted $path to ${temp.absolutePath}" }
                    return temp
                } catch (e: IOException) {
                    logger.fine { "Failed to extract $path: ${e.message}" }
                } finally {
                    try {
                        stream.close()
                    } catch (_: IOException) {
                    }
                }
            }
            // Also try JNA's own extraction helper
            for (path in candidates) {
                try {
                    val file = Native.extractFromResourcePath(path, GokfreHotkeyNative::class.java.classLoader)
                    if (file != null && file.exists()) {
                        logger.fine { "JNA extracted $path to ${file.absolutePath}" }
                        return file
                    }
                } catch (_: IOException) {
                }
            }
            logger.fine { "No embedded native library found in jar resources" }
            return null
        }
    }
}
