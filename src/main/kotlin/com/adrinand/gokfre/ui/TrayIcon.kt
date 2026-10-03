package com.adrinand.gokfre.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private const val ICON_RESOURCE = "gokfre.png"
private const val ICON_SIZE = 64
private const val ICON_MARGIN = 4
private const val FONT_SIZE = 32

/**
 * Loads the application tray icon from resources, falling back to a generated
 * purple circle with a white "K" if the resource is unavailable.
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

    val purple = Color(PURPLE_COLOR.toInt())
    graphics.color = purple
    graphics.fillOval(ICON_MARGIN, ICON_MARGIN, ICON_SIZE - ICON_MARGIN * 2, ICON_SIZE - ICON_MARGIN * 2)

    graphics.color = Color.WHITE
    graphics.font = Font("SansSerif", Font.BOLD, FONT_SIZE)
    val metrics = graphics.fontMetrics
    val text = "K"
    val textX = (ICON_SIZE - metrics.stringWidth(text)) / 2
    val textY = (ICON_SIZE + metrics.ascent) / 2 - 1
    graphics.drawString(text, textX, textY)

    graphics.dispose()
    return image
}
