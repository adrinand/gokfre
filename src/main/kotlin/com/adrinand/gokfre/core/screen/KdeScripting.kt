package com.adrinand.gokfre.core.screen

import com.adrinand.gokfre.ui.PREVIEW_WINDOW_TITLE
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import java.awt.Rectangle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger

private const val SCRIPT_RESOURCE = "/kde/gokfre-kde.js"
private const val TOKEN_PLACEHOLDER = "__GOKFRE_TOKEN__"
private const val OPERATION_PLACEHOLDER = "__GOKFRE_OPERATION__"
private const val WINDOW_ID_PLACEHOLDER = "__GOKFRE_WINDOW_ID__"
private const val X_PLACEHOLDER = "__GOKFRE_X__"
private const val Y_PLACEHOLDER = "__GOKFRE_Y__"
private const val WIDTH_PLACEHOLDER = "__GOKFRE_WIDTH__"
private const val HEIGHT_PLACEHOLDER = "__GOKFRE_HEIGHT__"
private const val PREVIEW_TITLE_PLACEHOLDER = "__GOKFRE_PREVIEW_TITLE__"
private const val CONTROL_CHAR_LIMIT = 0x20
private const val KWIN_SERVICE = "org.kde.KWin"
private const val SCRIPTING_PATH = "/Scripting"
private const val SCRIPT_RESULT_TIMEOUT_MS = 5_000L
private const val NEW_SCRIPT_PATH_PREFIX = "/Scripting/Script"
private const val LEGACY_SCRIPT_PATH_PREFIX = "/"
private const val PLUGIN_PREFIX = "gokfre_"
private val logger = Logger.getLogger("com.adrinand.gokfre.core.screen.KdeScriptRunner")

/**
 * Result reported by a generated KWin script through [KdeBridge].
 */
@Serializable
internal data class KdeScriptReply(
    val ok: Boolean = false,
    val error: String? = null,
    val id: String = "",
    val title: String = "",
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val maximized: Boolean = false,
)

@DBusInterfaceName("org.kde.kwin.Scripting")
private interface KWinScripting : DBusInterface {
    fun loadScript(
        filePath: String,
        pluginName: String,
    ): Int

    fun unloadScript(pluginName: String): Boolean

    override fun getObjectPath(): String
}

@DBusInterfaceName("org.kde.kwin.Script")
private interface KWinScript : DBusInterface {
    fun run()

    override fun getObjectPath(): String
}

/**
 * Renders the KWin script resource for one operation.
 */
@Suppress("TooManyFunctions")
internal object KdeScriptRenderer {
    private val template: String by lazy { loadTemplate() }

    fun capture(): String = render("capture")

    fun focus(handleId: String): String = render("focus", handleId)

    fun bounds(handleId: String): String = render("bounds", handleId)

    fun moveResize(
        handleId: String,
        bounds: Rectangle,
    ): String = render("moveResize", handleId, bounds)

    fun maximizePreview(): String = render("maximizePreview")

    fun isPreviewMaximized(): String = render("isPreviewMaximized")

    fun restorePreview(): String = render("restorePreview")

    fun probe(): String = render("probe")

    fun injectToken(
        script: String,
        token: String,
    ): String = script.replace(TOKEN_PLACEHOLDER, jsString(token))

    private fun render(
        operation: String,
        handleId: String = "",
        bounds: Rectangle? = null,
    ): String =
        template
            .replace(OPERATION_PLACEHOLDER, jsString(operation))
            .replace(WINDOW_ID_PLACEHOLDER, jsString(handleId))
            .replace(X_PLACEHOLDER, (bounds?.x ?: 0).toString())
            .replace(Y_PLACEHOLDER, (bounds?.y ?: 0).toString())
            .replace(WIDTH_PLACEHOLDER, (bounds?.width ?: 0).toString())
            .replace(HEIGHT_PLACEHOLDER, (bounds?.height ?: 0).toString())
            .replace(PREVIEW_TITLE_PLACEHOLDER, jsString(PREVIEW_WINDOW_TITLE))

    private fun loadTemplate(): String =
        KdeScriptRenderer::class.java.getResourceAsStream(SCRIPT_RESOURCE)?.bufferedReader()?.use { it.readText() }
            ?: error("Missing KWin script resource $SCRIPT_RESOURCE")

    private fun jsString(value: String): String =
        buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else ->
                        if (ch.code < CONTROL_CHAR_LIMIT) {
                            append("\\u%04x".format(ch.code))
                        } else {
                            append(ch)
                        }
                }
            }
            append('"')
        }
}

/**
 * Loads and runs one-off KWin scripts, collecting results via [KdeBridge].
 */
internal class KdeScriptRunner(
    private val bridge: KdeBridge,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun run(
        script: String,
        timeoutMs: Long = SCRIPT_RESULT_TIMEOUT_MS,
    ): KdeScriptReply? {
        val token = UUID.randomUUID().toString().replace("-", "")
        val pluginName = PLUGIN_PREFIX + token
        val future = bridge.register(token)
        val file = createScriptFile() ?: return null
        return try {
            Files.writeString(file, KdeScriptRenderer.injectToken(script, token))
            loadScript(pluginName, file)?.let { id ->
                runScript(id)
                bridge.await(token, future, timeoutMs)?.let(::parse)
            }
        } catch (e: DBusException) {
            logger.fine { "KWin scripting request failed: ${e.message}" }
            null
        } catch (e: DBusExecutionException) {
            logger.fine { "KWin scripting call failed: ${e.message}" }
            null
        } catch (e: IOException) {
            logger.fine { "KWin scripting I/O failed: ${e.message}" }
            null
        } finally {
            bridge.unregister(token)
            unloadScript(pluginName)
            deleteQuietly(file)
        }
    }

    private fun loadScript(
        pluginName: String,
        file: Path,
    ): Int? {
        val scripting = scriptingInterface() ?: return null
        val path = file.toAbsolutePath().toString()
        var id = scripting.loadScript(path, pluginName)
        if (id < 0) {
            scripting.unloadScript(pluginName)
            id = scripting.loadScript(path, pluginName)
        }
        if (id < 0) {
            logger.fine { "KWin refused to load script $pluginName" }
            return null
        }
        return id
    }

    private fun runScript(id: Int) {
        var lastFailure: DBusException? = null
        for (path in listOf("$NEW_SCRIPT_PATH_PREFIX$id", "$LEGACY_SCRIPT_PATH_PREFIX$id")) {
            lastFailure =
                try {
                    scriptInterface(path).run()
                    return
                } catch (e: DBusException) {
                    e
                } catch (e: DBusExecutionException) {
                    DBusException(e.message)
                }
        }
        throw lastFailure ?: DBusException("KWin script $id could not be executed")
    }

    private fun unloadScript(pluginName: String) {
        try {
            scriptingInterface()?.unloadScript(pluginName)
        } catch (e: DBusException) {
            logger.fine { "Could not unload KWin script $pluginName: ${e.message}" }
        } catch (e: DBusExecutionException) {
            logger.fine { "Could not unload KWin script $pluginName: ${e.message}" }
        }
    }

    private fun scriptingInterface(): KWinScripting? =
        try {
            bridge.connection().getRemoteObject(KWIN_SERVICE, SCRIPTING_PATH, KWinScripting::class.java, true)
        } catch (e: DBusException) {
            logger.fine { "KWin scripting interface unavailable: ${e.message}" }
            null
        } catch (e: IllegalArgumentException) {
            logger.fine { "Invalid KWin scripting interface: ${e.message}" }
            null
        }

    private fun scriptInterface(path: String): KWinScript =
        bridge.connection().getRemoteObject(KWIN_SERVICE, path, KWinScript::class.java, true)

    private fun createScriptFile(): Path? =
        try {
            Files.createTempFile("gokfre-kde-", ".js")
        } catch (e: IOException) {
            logger.fine { "Could not create KWin script file: ${e.message}" }
            null
        }

    private fun deleteQuietly(file: Path) {
        try {
            Files.deleteIfExists(file)
        } catch (e: IOException) {
            logger.fine { "Could not delete KWin script file $file: ${e.message}" }
        }
    }

    private fun parse(payload: String): KdeScriptReply? =
        try {
            json.decodeFromString<KdeScriptReply>(payload)
        } catch (e: IllegalArgumentException) {
            logger.fine { "Could not parse KWin script result: ${e.message}" }
            null
        }
}
