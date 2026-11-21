package com.adrinand.gokfre.utils

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Writes [content] to [target] through a same-directory temp file so readers
 * never observe a partial configuration file.
 */
internal fun writeAtomically(
    target: Path,
    content: String,
) {
    val directory = target.parent ?: error("Target path has no parent: $target")
    Files.createDirectories(directory)
    val temp = Files.createTempFile(directory, ".gokfre-", ".tmp")
    try {
        Files.writeString(temp, content, StandardCharsets.UTF_8)
        runCatching {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-r--r--"))
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}
