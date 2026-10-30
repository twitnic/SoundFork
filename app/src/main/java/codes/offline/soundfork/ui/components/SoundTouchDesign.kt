package codes.offline.soundfork

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared line icons keep the controls consistent without another icon dependency.
enum class TouchSymbol(val path: String) {
    Menu("M4,6 L20,6 M4,12 L20,12 M4,18 L20,18"),
    Grid("M3,3 L21,3 L21,21 L3,21 Z M9,3 L9,21 M15,3 L15,21 M3,9 L21,9 M3,15 L21,15"),
    Power("M12,2 L12,11 M6.4,5.6 A9,9 0,1 0,17.6 5.6"),
    Play("M8,4 L20,12 L8,20 Z"),
    Pause("M8,5 L8,19 M16,5 L16,19"),
    Stop("M6,6 L18,6 L18,18 L6,18 Z"),
    Up("M5,15 L12,8 L19,15"),
    Down("M5,9 L12,16 L19,9"),
    Search("M15.5,15.5 L21,21 M18,10.5 A7.5,7.5 0,1 1,3 10.5 A7.5,7.5 0,1 1,18 10.5"),
    Radio("M4,8 L20,8 L20,20 L4,20 Z M5,5 L18,2 M7,12 L7,16 M11,12 L17,12 M11,16 L17,16"),
    Speaker("M7,2 L17,2 L17,22 L7,22 Z M10,6 L14,6 M15,15 A3,3 0,1 1,9 15 A3,3 0,1 1,15 15"),
    Volume("M3,10 L7,10 L12,6 L12,18 L7,14 L3,14 Z M16,8 Q21,12 16,16 M19,5 Q26,12 19,19"),
    Muted("M3,10 L7,10 L12,6 L12,18 L7,14 L3,14 Z M17,9 L23,15 M23,9 L17,15"),
    Clock("M21,12 A9,9 0,1 1,3 12 A9,9 0,1 1,21 12 M12,6 L12,12 L16,15"),
    Source("M4,5 L20,5 L20,19 L4,19 Z M8,9 L12,13 L16,9"),
    Zone("M3,3 L10,3 L10,10 L3,10 Z M14,14 L21,14 L21,21 L14,21 Z M10,6 L18,6 L18,14 M6,10 L6,18 L14,18"),
    Debug("M8,5 L2,12 L8,19 M16,5 L22,12 L16,19 M14,3 L10,21"),
    Info("M21,12 A9,9 0,1 1,3 12 A9,9 0,1 1,21 12 M12,11 L12,17 M12,7 L12,8"),
    Back("M12,4 L4,12 L12,20 M4,12 L21,12"),
    Plus("M12,4 L12,20 M4,12 L20,12"),
    Close("M5,5 L19,19 M19,5 L5,19"),
    Heart("M12,20 C-6,9 5,-2 12,7 C19,-2 30,9 12,20 Z"),
    Check("M4,12 L9,17 L20,6"),
    Next("M9,5 L16,12 L9,19"),
    Settings("M5,6 L19,6 M5,12 L19,12 M5,18 L19,18 M9,3 L9,9 M15,9 L15,15 M10,15 L10,21")
}

@Composable
fun TouchIcon(symbol: TouchSymbol, description: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val vector = remember(symbol) {
        ImageVector.Builder(symbol.name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(symbol.path).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round
            )
        }.build()
    }
    Icon(vector, description, modifier.size(22.dp), tint = tint)
}

fun stationColors(name: String): List<Color> = when {
    name.contains("Chemnitz", true) -> listOf(Color(0xFFB67367), Color(0xFF593F46))
    name.contains("ENERGY", true) -> listOf(Color(0xFFB46455), Color(0xFF502A32))
    name.contains("Kultur", true) || name.contains("Klassik", true) -> listOf(Color(0xFF9A9675), Color(0xFF454A40))
    name.contains("BOB", true) -> listOf(Color(0xFF67717B), Color(0xFF202B37))
    else -> listOf(Color(0xFF718F97), Color(0xFF344E60))
}

@Composable
fun StationArtwork(name: String, modifier: Modifier = Modifier) {
    val colors = remember(name) { stationColors(name) }
    val monogram = remember(name) {
        name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "SF" }
    }
    Box(modifier.clip(MaterialTheme.shapes.extraSmall).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.57f
            repeat(4) { index ->
                drawCircle(Color.White.copy(alpha = 0.08f), radius * (0.5f + index * 0.24f),
                    center = Offset(size.width * 0.85f, size.height * 0.12f), style = Stroke(size.minDimension * 0.025f))
            }
            drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, size.height * 0.82f), Offset(size.width, size.height * 0.56f), 1.dp.toPx())
        }
        Text(monogram, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
    }
}

@Composable
fun SpeakerIllustration(modifier: Modifier = Modifier) {
    val circle = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.background(circle, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * 0.48f
            val h = size.height * 0.72f
            val x = (size.width - w) / 2
            val y = (size.height - h) / 2
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF30302F), Color(0xFF111111), Color(0xFF353534))), Offset(x, y), Size(w, h), CornerRadius(w * 0.1f))
            for (line in 1..15) {
                val ly = y + h * 0.35f + line * h * 0.035f
                drawLine(Color.White.copy(alpha = 0.09f), Offset(x + 4, ly), Offset(x + w - 4, ly), 1f)
            }
            drawRoundRect(Color(0xFFB3B1AC), Offset(x, y + h * 0.28f), Size(w, h * 0.045f), CornerRadius(1f))
            drawCircle(Color(0xFF91A78C), w * 0.025f, Offset(x + w * 0.7f, y + h * 0.12f))
        }
    }
}

@Composable
fun TouchSectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.titleSmall, letterSpacing = 1.sp)
}
