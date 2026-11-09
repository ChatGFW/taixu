package top.wkbin.taixu.ui.components

import top.wkbin.taixu.ui.miuix.RuntimeSpacing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * Group title above a card. Lists here already carry their own side padding, so the inset is
 * the card's inner padding rather than miuix's full-bleed 28dp, keeping the title aligned with
 * the row text below it.
 */
@Composable
fun RuntimeSectionTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(text = text, modifier = modifier, insideMargin = PaddingValues(horizontal = RuntimeSpacing.cardContent, vertical = 8.dp))
}
