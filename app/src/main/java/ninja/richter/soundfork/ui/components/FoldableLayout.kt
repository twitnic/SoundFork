package ninja.richter.soundfork

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import kotlin.math.ceil
import kotlin.math.floor

/** Window coordinates in physical pixels, as reported by WindowManager. */
data class WindowFold(val bounds: Rect, val orientation: FoldOrientation)
enum class FoldOrientation { VERTICAL, HORIZONTAL }

internal fun WindowLayoutInfo.separatingFolds(): List<WindowFold> =
    displayFeatures.filterIsInstance<FoldingFeature>()
        .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
        .map {
            WindowFold(
                Rect(it.bounds.left.toFloat(), it.bounds.top.toFloat(), it.bounds.right.toFloat(), it.bounds.bottom.toFloat()),
                if (it.orientation == FoldingFeature.Orientation.VERTICAL) FoldOrientation.VERTICAL else FoldOrientation.HORIZONTAL
            )
        }

internal data class FoldPanes(val first: Rect, val second: Rect, val orientation: FoldOrientation)

/** Translate the hinge to the inset content's coordinates; a zero-width crease is still a split. */
internal fun calculateFoldPanes(windowBounds: Rect, folds: List<WindowFold>): FoldPanes? {
    if (windowBounds.width <= 0 || windowBounds.height <= 0) return null
    for (fold in folds) {
        val bounds = fold.bounds
        if (fold.orientation == FoldOrientation.VERTICAL &&
            bounds.top <= windowBounds.top && bounds.bottom >= windowBounds.bottom &&
            bounds.left > windowBounds.left && bounds.right < windowBounds.right) {
            return FoldPanes(
                Rect(0f, 0f, floor(bounds.left - windowBounds.left), windowBounds.height),
                Rect(ceil(bounds.right - windowBounds.left), 0f, windowBounds.width, windowBounds.height),
                fold.orientation
            )
        }
        if (fold.orientation == FoldOrientation.HORIZONTAL &&
            bounds.left <= windowBounds.left && bounds.right >= windowBounds.right &&
            bounds.top > windowBounds.top && bounds.bottom < windowBounds.bottom) {
            return FoldPanes(
                Rect(0f, 0f, windowBounds.width, floor(bounds.top - windowBounds.top)),
                Rect(0f, ceil(bounds.bottom - windowBounds.top), windowBounds.width, windowBounds.height),
                fold.orientation
            )
        }
    }
    return null
}

internal fun FoldPanes.supportsPlayer(density: Float): Boolean =
    listOf(first, second).all { it.width / density >= 280f && it.height / density >= 180f }

internal fun FoldPanes.largestPane(): Rect =
    if (first.width * first.height >= second.width * second.height) first else second

// Dialogs must stay in the page pane when a separating fold is present.
internal val LocalFoldSeparated = staticCompositionLocalOf { false }
