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

import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

internal fun Modifier.a2uiAccessibility(
    attributes: A2uiBasicCatalogV1.AccessibilityAttributes?,
    isClickable: Boolean = false,
): Modifier =
    this.then(
        if (attributes == null) Modifier
        else
            Modifier.semantics {
                val contentDescription = attributes.toContentDescription(isClickable)
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }

                if (isClickable && !attributes.description.isNullOrBlank()) {
                    onClick(label = attributes.description, action = null)
                }
            }
    )

internal fun A2uiBasicCatalogV1.AccessibilityAttributes.toContentDescription(
    isClickable: Boolean = false
): String? {
    return if (isClickable) {
        label?.takeUnless { it.isBlank() }
    } else {
        buildContentDescription(label = label, description = description)
    }
}

internal fun buildContentDescription(label: String?, description: String?): String? {
    val cleanLabel = label?.takeUnless { it.isBlank() }
    val cleanDescription = description?.takeUnless { it.isBlank() }
    return when {
        cleanLabel != null && cleanDescription != null -> "$cleanLabel - $cleanDescription"
        cleanLabel != null -> cleanLabel
        cleanDescription != null -> cleanDescription
        else -> null
    }
}
