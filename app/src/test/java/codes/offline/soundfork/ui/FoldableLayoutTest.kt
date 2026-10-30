package codes.offline.soundfork

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class FoldableLayoutTest {
    @Test fun bookHingeUsesWindowOffsetAndExcludesOccludedPixels() {
        val panes = calculateFoldPanes(Rect(40f, 24f, 1240f, 824f), listOf(
            WindowFold(Rect(620f, 0f, 660f, 900f), FoldOrientation.VERTICAL)
        ))!!
        assertEquals(Rect(0f, 0f, 580f, 800f), panes.first)
        assertEquals(Rect(620f, 0f, 1200f, 800f), panes.second)
        assertTrue(panes.supportsPlayer(2f))
    }

    @Test fun tabletopCreaseWithZeroThicknessStillSeparatesPanes() {
        val panes = calculateFoldPanes(Rect(0f, 60f, 900f, 1200f), listOf(
            WindowFold(Rect(0f, 600f, 900f, 600f), FoldOrientation.HORIZONTAL)
        ))!!
        assertEquals(Rect(0f, 0f, 900f, 540f), panes.first)
        assertEquals(Rect(0f, 540f, 900f, 1140f), panes.second)
        assertTrue(panes.supportsPlayer(2f))
    }

    @Test fun fractionalBoundsRoundAwayFromTheHinge() {
        val panes = calculateFoldPanes(Rect(0f, 0f, 800f, 600f), listOf(
            WindowFold(Rect(300.4f, 0f, 320.4f, 600f), FoldOrientation.VERTICAL)
        ))!!
        assertEquals(300f, panes.first.right)
        assertEquals(321f, panes.second.left)
    }

    @Test fun missingOutsideAndPartialFeaturesDoNotSplitTheWindow() {
        val window = Rect(100f, 100f, 900f, 1100f)
        assertNull(calculateFoldPanes(window, emptyList()))
        for (bounds in listOf(Rect(20f, 0f, 20f, 1200f), Rect(900f, 0f, 920f, 1200f), Rect(500f, 200f, 500f, 800f))) {
            assertNull(calculateFoldPanes(window, listOf(WindowFold(bounds, FoldOrientation.VERTICAL))))
        }
        assertNull(calculateFoldPanes(window, listOf(WindowFold(Rect(0f, 50f, 1000f, 50f), FoldOrientation.HORIZONTAL))))
        assertNull(calculateFoldPanes(Rect.Zero, emptyList()))
    }

    @Test fun smallPaneFallsBackToTheLargerUnobstructedArea() {
        val panes = calculateFoldPanes(Rect(0f, 0f, 800f, 600f), listOf(
            WindowFold(Rect(160f, 0f, 180f, 600f), FoldOrientation.VERTICAL)
        ))!!
        assertFalse(panes.supportsPlayer(1f))
        assertEquals(Rect(180f, 0f, 800f, 600f), panes.largestPane())
        val shortPanes = calculateFoldPanes(Rect(0f, 0f, 1000f, 600f), listOf(
            WindowFold(Rect(0f, 140f, 1000f, 160f), FoldOrientation.HORIZONTAL)
        ))!!
        assertFalse(shortPanes.supportsPlayer(1f))
        assertEquals(shortPanes.second, shortPanes.largestPane())
    }
}
