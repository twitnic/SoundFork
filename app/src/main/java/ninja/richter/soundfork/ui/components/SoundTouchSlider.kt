package ninja.richter.soundfork

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundTouchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val active = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
    val track = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Slider(value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(Modifier.size(18.dp).shadow(2.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        },
        track = { slider ->
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val fraction = ((slider.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val start = Offset(if (rtl) size.width else 0f, size.height / 2)
                val end = Offset(if (rtl) 0f else size.width, size.height / 2)
                val current = Offset(if (rtl) size.width * (1 - fraction) else size.width * fraction, size.height / 2)
                drawLine(track, start, end, size.height, StrokeCap.Round)
                drawLine(active, start, current, size.height, StrokeCap.Round)
            }
        }
    )
}
