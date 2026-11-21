package com.adrinand.gokfre.utils

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal object PackagedAppPathFinder {
    fun codeSourcePath(): Path? {
        val location = PackagedAppPathFinder::class.java.protectionDomain?.codeSource?.location ?: return null
        return runCatching { Path.of(location.toURI()) }.getOrNull()
    }

    fun linuxExecutablePath(): Path? {
        val jar = codeSourcePath()?.toAbsolutePath()
        val appDir = jar?.parent
        val libDir = appDir?.parent
        val isPackaged =
            appDir?.fileName?.toString() == "app" && libDir?.fileName?.toString() == "lib"
        val installRoot = libDir?.parent.takeIf { isPackaged }
        val binDir = installRoot?.resolve("bin")?.takeIf { Files.isDirectory(it) }

        val launchers =
            binDir?.let { dir ->
                Files.list(dir).use { entries ->
                    entries
                        .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && Files.isExecutable(it) }
                        .toList()
                }
            } ?: return null

        val expectedName = jar?.fileName?.toString()?.removeSuffix(".jar")
        return launchers.firstOrNull { it.fileName.toString() == expectedName } ?: launchers.singleOrNull()
    }

    fun macAppBundlePath(): Path? {
        var path =
            codeSourcePath()?.toAbsolutePath()?.let { if (Files.isDirectory(it)) it else it.parent }
        while (path != null) {
            val isBundle =
                path.fileName?.toString()?.endsWith(".app") == true &&
                    Files.isDirectory(path.resolve("Contents").resolve("MacOS"))
            if (isBundle) {
                return path
            }
            path = path.parent
        }
        return null
    }
}
