package com.adrinand.gokfre.core.startup

import com.adrinand.gokfre.core.screen.isLinux
import com.adrinand.gokfre.core.screen.isMac
import java.nio.file.Path

private const val TRANSIENT_VOLUME_PREFIX = "/Volumes/"
private const val APP_TRANSLOCATION_MARKER = "/AppTranslocation/"

/**
 * Registers or removes the operating-system hook that launches gokfre at login.
 */
interface StartupLauncher {
    fun isEnabled(): Boolean

    fun enable(): Result<Unit>

    fun disable(): Result<Unit>
}

fun createStartupLauncher(): StartupLauncher =
    when {
        isLinux() -> LinuxStartupLauncher()
        isMac() -> MacStartupLauncher()
        else -> throw UnsupportedOperationException("Launch at startup is not supported on this OS.")
    }

internal fun Path.requireNoControlCharacters(description: String): Path {
    require(toAbsolutePath().toString().none { it.isISOControl() }) {
        "The $description path contains unsupported control characters."
    }
    return this
}

internal fun Path.requirePermanentMacLocation(): Path {
    val path = toAbsolutePath().toString()
    require(!path.startsWith(TRANSIENT_VOLUME_PREFIX) && !path.contains(APP_TRANSLOCATION_MARKER)) {
        "Launch at startup requires Gokfre.app to be installed outside a disk image or temporary location."
    }
    return this
}