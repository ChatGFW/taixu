package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.feature.settings.R

/** Decorative illustration uses the active palette, including wallpaper colors. */
@Composable
internal fun PersonalColorPreview() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(148.dp).clearAndSetSemantics { }) {
            val radius = size.height * .32f
            val center = Offset(size.width * .5f, size.height * .5f)
            for (angle in 0 until 360 step 60) {
                rotate(angle.toFloat(), center) {
                    drawOval(colors.primaryContainer,
                        topLeft = Offset(center.x - radius * .48f, center.y - radius * 1.45f),
                        size = Size(radius * .96f, radius * 1.65f))
                }
            }
            drawCircle(colors.primary, radius * .55f, center)
            drawCircle(colors.tertiaryContainer, radius * .48f, Offset(center.x + radius * 2.25f, center.y - radius * .7f))
            drawCircle(colors.tertiary, radius * .22f, Offset(center.x + radius * 2.25f, center.y - radius * .7f))
            drawCircle(colors.secondaryContainer, radius * .45f, Offset(center.x - radius * 2.2f, center.y + radius * .65f))
        }
        Text(stringResource(R.string.personal_color_intro), modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
    }
}
