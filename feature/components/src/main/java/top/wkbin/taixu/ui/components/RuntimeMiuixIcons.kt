package top.wkbin.taixu.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

/**
 * Only names whose miuix glyph carries the same meaning are mapped; everything else falls back to
 * the bundled drawable. A near-miss glyph (clock for globe, mind map for model) reads as a bug.
 */
internal fun runtimeIconVector(name: RuntimeIconName): ImageVector? = when (name) {
    RuntimeIconName.Home -> MiuixIcons.Home
    RuntimeIconName.Settings, RuntimeIconName.NavSettings -> MiuixIcons.Settings
    RuntimeIconName.Back -> MiuixIcons.Back
    RuntimeIconName.Refresh -> MiuixIcons.Refresh
    RuntimeIconName.Close -> MiuixIcons.Close
    RuntimeIconName.Download -> MiuixIcons.Download
    RuntimeIconName.Play -> MiuixIcons.Play
    RuntimeIconName.More -> MiuixIcons.More
    RuntimeIconName.Copy -> MiuixIcons.Copy
    RuntimeIconName.Folder, RuntimeIconName.FolderOpen, RuntimeIconName.Workspace,
    RuntimeIconName.NavRepository -> MiuixIcons.Folder
    RuntimeIconName.File, RuntimeIconName.Document -> MiuixIcons.File
    RuntimeIconName.Edit, RuntimeIconName.WriteDocument -> MiuixIcons.Edit
    RuntimeIconName.Search -> MiuixIcons.Search
    RuntimeIconName.Info -> MiuixIcons.Info
    RuntimeIconName.Image -> MiuixIcons.Image
    RuntimeIconName.VolumeOff -> MiuixIcons.VolumeOff
    RuntimeIconName.VolumeOn -> MiuixIcons.VolumeUp
    RuntimeIconName.Update -> MiuixIcons.Update
    RuntimeIconName.Tune -> MiuixIcons.Tune
    RuntimeIconName.Community -> MiuixIcons.Community
    RuntimeIconName.Link -> MiuixIcons.Link
    RuntimeIconName.Alarm -> MiuixIcons.Alarm
    RuntimeIconName.Phone -> MiuixIcons.Phone
    RuntimeIconName.ChevronRight -> MiuixIcons.ChevronForward
    RuntimeIconName.NavDashboard -> MiuixIcons.GridView
    RuntimeIconName.Shield -> MiuixIcons.Lock
    RuntimeIconName.Trash -> MiuixIcons.Delete
    RuntimeIconName.Check -> MiuixIcons.Ok
    RuntimeIconName.Plus -> MiuixIcons.Add
    RuntimeIconName.List -> MiuixIcons.ListView
    RuntimeIconName.Palette -> MiuixIcons.Theme
    RuntimeIconName.FolderDownload -> MiuixIcons.FileDownloads
    RuntimeIconName.Visibility -> MiuixIcons.Show
    RuntimeIconName.VisibilityOff -> MiuixIcons.Hide
    RuntimeIconName.Mail -> MiuixIcons.Email
    RuntimeIconName.History -> MiuixIcons.Recent
    RuntimeIconName.Checklist -> MiuixIcons.Tasks
    RuntimeIconName.Reverse -> MiuixIcons.Undo
    RuntimeIconName.Menu, RuntimeIconName.PanelLeftClose -> MiuixIcons.Sidebar
    else -> null
}
