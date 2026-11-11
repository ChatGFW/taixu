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
import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Slider
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.roundToInt

/** A Jetpack Compose Material 3 implementation of the A2UI Basic Catalog `"Slider"` component. */
internal object MiuixA2uiBasicCatalogV1Slider : A2uiBasicCatalogV1.Slider {

    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String?,
        min: Float,
        max: Float,
        value: Float,
        onValueChange: (Float) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        val failedCheck = checks.fastFirstOrNull { !it.condition }
        val errorMessage = failedCheck?.message

        // TODO(b/549060875): Figure out how this should be reflected in the UI: switch back to the
        //  loading state or show some kind of error.
        if (min > max) {
            SideEffect(min, max) {
                reportError(
                    A2uiException.A2uiRuntimeException(
                        "Min value cannot be greater than max value."
                    )
                )
            }
        } else {
            val coercedValue = value.coerceIn(min, max)
            val valueRange = min..max
            val steps = (max.toInt() - min.toInt() - 1).coerceAtLeast(0)

            Column(
                modifier = modifier.then(SliderBottomPaddingModifier),
                verticalArrangement = SliderVerticalArrangement,
            ) {
                Row(
                    modifier = SliderHeaderRowModifier,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (label != null) {
                        Text(text = label, modifier = Modifier.weight(1f, fill = false))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(text = coercedValue.roundToInt().toString())
                }

                Slider(
                    value = coercedValue,
                    valueRange = if (min == max) min..(max + 1f) else valueRange,
                    steps = steps,
                    modifier =
                        Modifier.sliderAccessibility(accessibility = accessibility, label = label)
                            .semantics {
                                if (!errorMessage.isNullOrBlank()) {
                                    error(errorMessage)
                                }
                            },
                    onValueChange = onValueChange,
                    enabled = enabled && min < max,
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = RuntimeTheme.colorScheme.error,
                        style = RuntimeTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val SliderVerticalArrangement = Arrangement.spacedBy(4.dp)
private val SliderHeaderRowModifier = Modifier.fillMaxWidth()
private val SliderBottomPaddingModifier = Modifier.padding(bottom = 8.dp)

/**
 * Applies slider-specific accessibility semantics using [accessibility] and visual [label].
 *
 * Sets [contentDescription] on the slider using the accessibility label if non-blank, falling back
 * to the visual [label], and combines with [accessibility] description if present.
 */
private fun Modifier.sliderAccessibility(
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
    label: String?,
): Modifier {
    val effectiveLabel =
        accessibility?.label?.takeUnless { it.isBlank() } ?: label?.takeUnless { it.isBlank() }
    val contentDescription =
        buildContentDescription(
            label = effectiveLabel,
            description = accessibility?.description,
        ) ?: return this
    return semantics { this.contentDescription = contentDescription }
}
