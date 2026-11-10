package top.wkbin.taixu.feature.a2uipoc.miuix

import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.compose.ui.catalog.toA2uiCatalog

internal fun miuixA2uiBasicCatalogV1(
    image: A2uiBasicCatalogV1.Image,
    video: A2uiBasicCatalogV1.Video,
    audioPlayer: A2uiBasicCatalogV1.AudioPlayer,
    functions: A2uiBasicCatalogV1.Functions,
    list: A2uiBasicCatalogV1.List,
): A2uiCatalog = A2uiBasicCatalogV1(
    image = image, video = video, audioPlayer = audioPlayer, functions = functions, list = list,
    text = MiuixA2uiBasicCatalogV1Text,
    icon = MiuixA2uiBasicCatalogV1Icon,
    row = MiuixA2uiBasicCatalogV1Row,
    column = MiuixA2uiBasicCatalogV1Column,
    card = MiuixA2uiBasicCatalogV1Card,
    tabs = MiuixA2uiBasicCatalogV1Tabs,
    modal = MiuixA2uiBasicCatalogV1Modal,
    divider = MiuixA2uiBasicCatalogV1Divider,
    button = MiuixA2uiBasicCatalogV1Button,
    textField = MiuixA2uiBasicCatalogV1TextField,
    checkBox = MiuixA2uiBasicCatalogV1CheckBox,
    choicePicker = MiuixA2uiBasicCatalogV1ChoicePicker,
    slider = MiuixA2uiBasicCatalogV1Slider,
    dateTimeInput = MiuixA2uiBasicCatalogV1DateTimeInput,
).toA2uiCatalog()
