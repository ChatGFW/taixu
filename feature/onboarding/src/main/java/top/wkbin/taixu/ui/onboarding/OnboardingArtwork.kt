@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package top.wkbin.taixu.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName

/** Theme-colored Compose illustration; decorative content stays out of the accessibility tree. */
@Composable
internal fun OnboardingArtwork() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(232.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Surface(Modifier.size(224.dp).rotate(-12f), shape = MaterialShapes.Puffy.toShape(), color = colors.primaryContainer) {}
        Surface(Modifier.align(Alignment.TopEnd).padding(end = 24.dp).size(40.dp).rotate(15f),
            shape = MaterialShapes.Cookie7Sided.toShape(), color = colors.tertiaryContainer) {}
        Surface(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 8.dp).size(32.dp),
            shape = CircleShape, color = colors.secondaryContainer) {}
        RuntimeCard(
            modifier = Modifier.width(200.dp).rotate(-6f),
            containerColor = colors.surfaceContainerHighest,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { Surface(Modifier.size(6.dp), shape = CircleShape, color = colors.outline) {} }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("~ / taixu", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                Text(">_", style = MaterialTheme.typography.displayMedium, color = colors.primary, fontFamily = FontFamily.Monospace)
                Surface(Modifier.fillMaxWidth(0.7f).height(6.dp), shape = CircleShape, color = colors.outlineVariant) {}
                Surface(Modifier.fillMaxWidth(0.45f).height(6.dp), shape = CircleShape, color = colors.outlineVariant) {}
            }
        }
        Surface(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 8.dp).size(64.dp).rotate(12f),
            shape = MaterialTheme.shapes.extraLarge, color = colors.tertiaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(32.dp), tint = colors.onTertiaryContainer)
            }
        }
    }
}
