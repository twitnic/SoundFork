package ninja.richter.soundfork

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import org.junit.Assert.*
import org.junit.Test

class FoldingFeatureMappingTest {
    @Test fun flatContinuousDisplayKeepsTheResponsiveLayout() {
        assertTrue(WindowLayoutInfo(listOf(feature(separating = false))).separatingFolds().isEmpty())
        assertTrue(WindowLayoutInfo(emptyList()).separatingFolds().isEmpty())
    }

    @Test fun halfOpenAndOccludingHingesArePassedToTheLayout() {
        val halfOpen = feature(separating = true, state = FoldingFeature.State.HALF_OPENED)
        val hinge = feature(separating = false, occlusion = FoldingFeature.OcclusionType.FULL)
        val folds = WindowLayoutInfo(listOf(halfOpen, hinge)).separatingFolds()
        assertEquals(2, folds.size)
        assertEquals(FoldOrientation.VERTICAL, folds.first().orientation)
        assertEquals(500f, folds.first().bounds.left)
        val tabletop = WindowLayoutInfo(listOf(feature(separating = true,
            orientation = FoldingFeature.Orientation.HORIZONTAL))).separatingFolds()
        assertEquals(FoldOrientation.HORIZONTAL, tabletop.single().orientation)
    }

    private fun feature(
        separating: Boolean,
        state: FoldingFeature.State = FoldingFeature.State.FLAT,
        occlusion: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE,
        orientation: FoldingFeature.Orientation = FoldingFeature.Orientation.VERTICAL
    ): FoldingFeature = object : FoldingFeature {
        override val bounds = Rect(500, 0, 500, 1000)
        override val isSeparating = separating
        override val state = state
        override val occlusionType = occlusion
        override val orientation = orientation
    }
}
