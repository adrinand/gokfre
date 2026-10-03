package com.adrinand.gokfre.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private const val ICON_RESOURCE = "gokfre-tray.png"
private const val ICON_SIZE = 64
private const val BODY_MARGIN = 4
private const val BODY_RADIUS = 24
private const val HOLE_SIZE = 16
private const val HOLE_RADIUS = 10
private const val HOLE_FIRST = 12
private const val HOLE_STEP = 24
private const val HOLE_COUNT = 2
private val HOLE_POSITIONS = IntArray(HOLE_COUNT) { HOLE_FIRST + it * HOLE_STEP }

/**
 * Loads the application tray icon from resources, falling back to a generated
 * white waffle if the resource is unavailable.
 */
fun createTrayIcon(): Painter = BitmapPainter(createTrayImage().toComposeImageBitmap())

/**
 * Loads or generates the tray icon image.
 */
fun createTrayImage(): BufferedImage {
    val resource = Thread.currentThread().contextClassLoader.getResource(ICON_RESOURCE)
    return resource?.let { ImageIO.read(it) } ?: generateTrayImage()
}

private fun generateTrayImage(): BufferedImage {
    val image = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    graphics.color = Color.WHITE
    graphics.fillRoundRect(
        BODY_MARGIN,
        BODY_MARGIN,
        ICON_SIZE - BODY_MARGIN * 2,
        ICON_SIZE - BODY_MARGIN * 2,
        BODY_RADIUS,
        BODY_RADIUS,
    )

    graphics.composite = AlphaComposite.Clear
    for (y in HOLE_POSITIONS) {
        for (x in HOLE_POSITIONS) {
            graphics.fillRoundRect(x, y, HOLE_SIZE, HOLE_SIZE, HOLE_RADIUS, HOLE_RADIUS)
        }
    }

    graphics.dispose()
    return image
}
