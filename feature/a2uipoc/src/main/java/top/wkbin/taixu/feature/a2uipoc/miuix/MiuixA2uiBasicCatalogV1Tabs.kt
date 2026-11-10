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
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.taixu.ui.miuix.Tab
import top.wkbin.taixu.feature.a2uipoc.miuix.MiuixA2uiDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

internal object MiuixA2uiBasicCatalogV1Tabs : A2uiBasicCatalogV1.Tabs {

    @Composable
    override fun A2uiComponentScope.TypedContent(
        tabs: List<A2uiBasicCatalogV1.Tabs.Tab>,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        if (tabs.isEmpty()) return

        var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
        val coercedSelectedTabIndex = selectedTabIndex.coerceIn(0, tabs.size - 1)
        if (selectedTabIndex >= tabs.size) {
            selectedTabIndex = tabs.size - 1
        }

        Column(modifier = modifier.a2uiAccessibility(accessibility)) {
            top.yukonga.miuix.kmp.basic.TabRow(
                tabs = tabs.map { it.title },
                selectedTabIndex = coercedSelectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
            )

            val activeTab = tabs[coercedSelectedTabIndex]
            val childId = activeTab.childId
            val childState = observeA2uiComponentState(id = childId)
            AnimatedContent(
                targetState = childState,
                contentKey = { state ->
                    when (state) {
                        is A2uiComponentState.Loading -> "loading"
                        is A2uiComponentState.Error -> "error"
                        is A2uiComponentState.Success -> Pair(childId, state.component.type)
                    }
                },
                transitionSpec = MiuixA2uiDefaults.transitionSpec(),
                label = "TabContentTransition",
                modifier = TabContainerModifier,
            ) { state ->
                when (state) {
                    is A2uiComponentState.Loading -> {
                        MiuixA2uiDefaults.LoadingIndicator(modifier = TabLoadingModifier)
                    }

                    is A2uiComponentState.Error -> {
                        MiuixA2uiDefaults.ErrorFallback(
                            exception = state.exception,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is A2uiComponentState.Success -> {
                        A2uiComponent(component = state.component)
                    }
                }
            }
        }
    }
}

private val TabItemModifier = Modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
private val TabContainerModifier = Modifier.fillMaxWidth().padding(top = 16.dp)
private val TabLoadingModifier = Modifier.fillMaxWidth().height(120.dp)
