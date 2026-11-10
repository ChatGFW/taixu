/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted for TaiXu: render the A2UI protocol with Miuix components.
package top.wkbin.taixu.feature.a2uipoc.miuix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import top.wkbin.taixu.ui.miuix.HorizontalDivider
import top.wkbin.taixu.ui.miuix.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal object MiuixA2uiBasicCatalogV1Divider : A2uiBasicCatalogV1.Divider {

    @Composable
    override fun A2uiComponentScope.TypedContent(
        axis: A2uiBasicCatalogV1.Divider.Axis,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val dividerModifier = modifier.a2uiAccessibility(accessibility)
        when (axis) {
            A2uiBasicCatalogV1.Divider.Axis.Horizontal ->
                HorizontalDivider(modifier = dividerModifier)
            A2uiBasicCatalogV1.Divider.Axis.Vertical -> VerticalDivider(modifier = dividerModifier)
        }
    }
}
