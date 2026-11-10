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
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Icon
import top.wkbin.taixu.feature.a2uipoc.miuix.MiuixA2uiDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A Jetpack Compose Material 3 implementation of the A2UI Basic Catalog `"Icon"` component. */
internal object MiuixA2uiBasicCatalogV1Icon : A2uiBasicCatalogV1.Icon {

    @Composable
    override fun A2uiComponentScope.TypedContent(
        source: A2uiBasicCatalogV1.Icon.Source,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val icon =
            when (source) {
                is A2uiBasicCatalogV1.Icon.BuiltIn -> {
                    miuixIconFromName(source.value)
                }
                is A2uiBasicCatalogV1.Icon.SvgPath -> {
                    rememberVectorFromPath(source.svgPath)
                }
                is A2uiBasicCatalogV1.Icon.Unrecognized -> {
                    null
                }
            }

        AnimatedContent(
            modifier = modifier,
            targetState = icon,
            transitionSpec = MiuixA2uiDefaults.transitionSpec(),
            contentKey = { state ->
                if (state != null) {
                    "icon"
                } else {
                    "loading"
                }
            },
            label = "IconTransition",
        ) { state ->
            if (state == null) {
                SideEffect(source) {
                    val errorMessage =
                        when (source) {
                            is A2uiBasicCatalogV1.Icon.BuiltIn ->
                                "Unknown icon '${source.value}'. Expected a valid icon token or " +
                                    "an object with 'svgPath'."
                            is A2uiBasicCatalogV1.Icon.SvgPath ->
                                "Failed to parse SVG path '${source.svgPath}'."
                            is A2uiBasicCatalogV1.Icon.Unrecognized ->
                                "Unknown icon '${source.name}'. Expected a valid icon token or " +
                                    "an object with 'svgPath'."
                        }

                    // TODO(b/549592297): Add the path to the problematic property in the error
                    //  context once available.
                    reportError(A2uiException.A2uiRuntimeException(message = errorMessage))
                }

                MiuixA2uiDefaults.LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = state,
                    contentDescription = accessibility?.toContentDescription(),
                )
            }
        }
    }
}

@Composable
private fun rememberVectorFromPath(pathData: String): ImageVector? =
    remember(pathData) {
        runCatching {
            val viewportSize = 24f
            val pathNodes = PathParser().parsePathString(pathData).toNodes()

            ImageVector.Builder(
                    name = "SvgIcon",
                    defaultWidth = viewportSize.dp,
                    defaultHeight = viewportSize.dp,
                    viewportWidth = viewportSize,
                    viewportHeight = viewportSize,
                )
                .addPath(pathData = pathNodes, fill = DefaultSvgFill)
                .build()
        }
            .getOrNull()
    }

private val DefaultSvgFill = SolidColor(Color.Black)

private fun miuixIconFromName(name: String): ImageVector? = when (name) {
    "accountCircle" -> MiuixIcons.ContactsCircle
    "add" -> MiuixIcons.Add
    "arrowBack" -> MiuixIcons.Back
    "arrowForward" -> MiuixIcons.Forward
    "attachFile" -> MiuixIcons.Link
    "calendarToday" -> MiuixIcons.Months
    "call" -> MiuixIcons.Phone
    "camera" -> MiuixIcons.Scan
    "check" -> MiuixIcons.Ok
    "close" -> MiuixIcons.Close
    "delete" -> MiuixIcons.Delete
    "download" -> MiuixIcons.Download
    "edit" -> MiuixIcons.Edit
    "event" -> MiuixIcons.Alarm
    "error" -> MiuixIcons.Report
    "fastForward" -> MiuixIcons.Forward
    "favorite" -> MiuixIcons.FavoritesFill
    "favoriteOff" -> MiuixIcons.Favorites
    "folder" -> MiuixIcons.Folder
    "help" -> MiuixIcons.Help
    "home" -> MiuixIcons.Home
    "info" -> MiuixIcons.Info
    "locationOn" -> MiuixIcons.Location
    "lock" -> MiuixIcons.Lock
    "lockOpen" -> MiuixIcons.Unlock
    "mail" -> MiuixIcons.Email
    "menu" -> MiuixIcons.Sidebar
    "moreVert" -> MiuixIcons.More
    "moreHoriz" -> MiuixIcons.More
    "notificationsOff" -> MiuixIcons.VolumeOff
    "notifications" -> MiuixIcons.Alarm
    "pause" -> MiuixIcons.Pause
    "payment" -> MiuixIcons.BankCards
    "person" -> MiuixIcons.Contacts
    "phone" -> MiuixIcons.Phone
    "photo" -> MiuixIcons.Image
    "play" -> MiuixIcons.Play
    "print" -> MiuixIcons.File
    "refresh" -> MiuixIcons.Refresh
    "rewind" -> MiuixIcons.Back
    "search" -> MiuixIcons.Search
    "send" -> MiuixIcons.Send
    "settings" -> MiuixIcons.Settings
    "share" -> MiuixIcons.Share
    "shoppingCart" -> MiuixIcons.Store
    "skipNext" -> MiuixIcons.Forward
    "skipPrevious" -> MiuixIcons.Back
    "star" -> MiuixIcons.FavoritesFill
    "starHalf" -> MiuixIcons.Favorites
    "starOff" -> MiuixIcons.Favorites
    "stop" -> MiuixIcons.Pause
    "upload" -> MiuixIcons.UploadCloud
    "visibility" -> MiuixIcons.Show
    "visibilityOff" -> MiuixIcons.Hide
    "volumeDown" -> MiuixIcons.VolumeUp
    "volumeMute" -> MiuixIcons.VolumeOff
    "volumeOff" -> MiuixIcons.VolumeOff
    "volumeUp" -> MiuixIcons.VolumeUp
    "warning" -> MiuixIcons.Report
    else -> null
}
