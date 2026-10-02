package com.adrinand.kofre.core.screen

import java.awt.Rectangle

private const val MIN_DIMENSION = 1

/**
 * Position of a single cell inside the raw layout grid.
 */
data class TilePosition(
    val row: Int,
    val col: Int,
)

/**
 * Returns all layout positions whose label matches one of [selectedKeys].
 *
 * Matching is case-insensitive. Because layout keys are unique, this normally
 * returns one position per distinct key.
 */
fun findKeyPositions(
    keyLabels: List<List<String>>,
    selectedKeys: List<String>,
): List<TilePosition> {
    val normalized = selectedKeys.map { it.uppercase() }.toSet()
    return buildList {
        keyLabels.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, label ->
                if (label.uppercase() in normalized) {
                    add(TilePosition(row, col))
                }
            }
        }
    }
}

/**
 * Computes the screen bounds for a set of [positions] inside a weighted layout.
 *
 * The returned rectangle is the bounding box of the selected cells mapped to
 * [workArea]. Hidden rows/columns (weight == 0) are ignored, so the result
 * reflects the visible grid.
 *
 * Returns `null` when the selection does not map to any visible cell.
 */
fun computeSelectedBounds(
    rowWeights: List<Int>,
    columnWeights: List<Int>,
    positions: List<TilePosition>,
    workArea: Rectangle,
): Rectangle? {
    val visibleRows = rowWeights.indices.filter { rowWeights[it] > 0 }
    val visibleCols = columnWeights.indices.filter { columnWeights[it] > 0 }

    val visualPositions =
        positions.mapNotNull { pos ->
            val visualRow = visibleRows.indexOf(pos.row).takeIf { it >= 0 } ?: return@mapNotNull null
            val visualCol = visibleCols.indexOf(pos.col).takeIf { it >= 0 } ?: return@mapNotNull null
            TilePosition(visualRow, visualCol)
        }

    val totalRowWeight = visibleRows.sumOf { rowWeights[it] }
    val totalColWeight = visibleCols.sumOf { columnWeights[it] }

    if (visualPositions.isEmpty()) {
        return null
    }

    if (totalRowWeight == 0 || totalColWeight == 0) {
        return null
    }

    val minVisualRow = visualPositions.minOf { it.row }
    val maxVisualRow = visualPositions.maxOf { it.row }
    val minVisualCol = visualPositions.minOf { it.col }
    val maxVisualCol = visualPositions.maxOf { it.col }

    val visibleRowWeights = visibleRows.map { rowWeights[it] }
    val visibleColWeights = visibleCols.map { columnWeights[it] }

    val rowStart = visibleRowWeights.take(minVisualRow).sum().toDouble() / totalRowWeight
    val rowEnd = visibleRowWeights.take(maxVisualRow + MIN_DIMENSION).sum().toDouble() / totalRowWeight
    val colStart = visibleColWeights.take(minVisualCol).sum().toDouble() / totalColWeight
    val colEnd = visibleColWeights.take(maxVisualCol + MIN_DIMENSION).sum().toDouble() / totalColWeight

    val x = workArea.x + (colStart * workArea.width).toInt()
    val y = workArea.y + (rowStart * workArea.height).toInt()
    val right = workArea.x + (colEnd * workArea.width).toInt()
    val bottom = workArea.y + (rowEnd * workArea.height).toInt()

    return Rectangle(x, y, right - x, bottom - y)
}
