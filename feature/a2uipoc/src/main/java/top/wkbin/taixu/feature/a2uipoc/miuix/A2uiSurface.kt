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

import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.LocalA2uiReadinessEvaluator
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.asReadinessEvaluator
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.wkbin.taixu.ui.miuix.CircularProgressIndicator
import top.wkbin.taixu.ui.miuix.RuntimeTheme
import top.wkbin.taixu.ui.miuix.Surface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
public fun A2uiSurface(
    surfaceModel: A2uiSurfaceModel,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { A2uiSurfaceDefaults.LoadingIndicator() },
    errorContent: @Composable (A2uiException) -> Unit = { A2uiSurfaceDefaults.ErrorFallback(it) },
    transitionSpec: (AnimatedContentTransitionScope<A2uiComponentState>.() -> ContentTransform)? =
        A2uiSurfaceDefaults.transitionSpec,
) {
    require(surfaceModel is A2uiCoreSurfaceModel) {
        "A2uiSurface requires an A2uiCoreSurfaceModel."
    }
    val composeCatalog =
        requireNotNull(surfaceModel.catalog as? A2uiCatalog) {
            "A2uiSurface requires an A2uiCatalog."
        }
    val readinessEvaluator = remember(composeCatalog) { composeCatalog.asReadinessEvaluator() }

    CompositionLocalProvider(LocalA2uiReadinessEvaluator provides readinessEvaluator) {
        val rootState = observeA2uiComponentState(surface = surfaceModel)
        if (transitionSpec != null) {
            AnimatedContent(
                targetState = rootState,
                contentKey = { state ->
                    when (state) {
                        is A2uiComponentState.Loading -> "loading"
                        is A2uiComponentState.Error -> "error"
                        is A2uiComponentState.Success ->
                            Pair(state.component.surface.id, state.component.type)
                    }
                },
                transitionSpec = transitionSpec,
                label = "A2uiSurfaceTransition",
                modifier = modifier,
            ) { targetState ->
                when (targetState) {
                    is A2uiComponentState.Loading -> loadingContent()
                    is A2uiComponentState.Error -> errorContent(targetState.exception)
                    is A2uiComponentState.Success ->
                        A2uiComponent(component = targetState.component)
                }
            }
        } else {
            Box(modifier = modifier) {
                when (rootState) {
                    is A2uiComponentState.Loading -> loadingContent()
                    is A2uiComponentState.Error -> errorContent(rootState.exception)
                    is A2uiComponentState.Success -> A2uiComponent(component = rootState.component)
                }
            }
        }
    }
}

public object A2uiSurfaceDefaults {

    @Composable
    public fun LoadingIndicator() {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = RuntimeTheme.colorScheme.primary)
        }
    }

    @Composable
    public fun ErrorFallback(exception: A2uiException) {
        Surface(
            color = RuntimeTheme.colorScheme.errorContainer,
            shape = RuntimeTheme.shapes.medium,
        ) {
            Text(text = exception.message.orEmpty(), modifier = Modifier.padding(16.dp))
        }
    }

    public val transitionSpec:
        AnimatedContentTransitionScope<A2uiComponentState>.() -> ContentTransform
        @Composable
        get() {
            val defaultEffectsSpec = RuntimeTheme.motionScheme.defaultEffectsSpec<Float>()
            val fastEffectsSpec = RuntimeTheme.motionScheme.fastEffectsSpec<Float>()
            val defaultSpatialSpec = RuntimeTheme.motionScheme.defaultSpatialSpec<IntSize>()

            return remember(defaultEffectsSpec, fastEffectsSpec, defaultSpatialSpec) {
                {
                    (fadeIn(animationSpec = defaultEffectsSpec) togetherWith
                            fadeOut(animationSpec = fastEffectsSpec))
                        .using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ -> defaultSpatialSpec },
                            )
                        )
                }
            }
        }
}
