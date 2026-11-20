package top.wkbin.taixu.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.*

/**
 * Only names whose miuix glyph carries the same meaning are mapped; everything else falls back to
 * the bundled drawable. A near-miss glyph (clock for globe, mind map for model) reads as a bug.
 */
internal fun runtimeIconVector(name: RuntimeIconName): ImageVector? = when (name) {
    RuntimeIconName.Home -> MiuixIcons.Glass.HomeOutline
    RuntimeIconName.Settings, RuntimeIconName.NavSettings -> MiuixIcons.Glass.SettingsOutline
    RuntimeIconName.Back -> MiuixIcons.Glass.Back
    RuntimeIconName.Refresh -> MiuixIcons.Glass.Refresh
    RuntimeIconName.Close -> MiuixIcons.Glass.Close
    RuntimeIconName.Download -> MiuixIcons.Glass.Download
    RuntimeIconName.Play -> MiuixIcons.Glass.Play
    RuntimeIconName.More -> MiuixIcons.Glass.More
    RuntimeIconName.Copy -> MiuixIcons.Glass.Copy
    RuntimeIconName.Save -> MiuixIcons.Glass.Save
    RuntimeIconName.Send -> MiuixIcons.Glass.Send
    RuntimeIconName.Chat, RuntimeIconName.NavMessage -> MiuixIcons.Glass.Messages
    RuntimeIconName.Camera -> MiuixIcons.Glass.CameraOutline
    RuntimeIconName.Bookmark -> MiuixIcons.Glass.BookmarkOutline
    RuntimeIconName.Key -> MiuixIcons.Glass.Key
    RuntimeIconName.Folder, RuntimeIconName.FolderOpen, RuntimeIconName.Workspace,
    RuntimeIconName.NavRepository -> MiuixIcons.Glass.FolderOutline
    RuntimeIconName.File, RuntimeIconName.Document -> MiuixIcons.Glass.File
    RuntimeIconName.Edit, RuntimeIconName.WriteDocument -> MiuixIcons.Glass.Edit
    RuntimeIconName.Search -> MiuixIcons.Glass.Search
    RuntimeIconName.Info -> MiuixIcons.Glass.Info
    RuntimeIconName.Image -> MiuixIcons.Glass.ImageOutline
    RuntimeIconName.VolumeOff -> MiuixIcons.Glass.VolumeOff
    RuntimeIconName.VolumeOn -> MiuixIcons.Glass.VolumeUp
    RuntimeIconName.Update -> MiuixIcons.Glass.Update
    RuntimeIconName.Tune -> MiuixIcons.Glass.Tune
    RuntimeIconName.Community -> MiuixIcons.Glass.Community
    RuntimeIconName.Link -> MiuixIcons.Glass.Link
    RuntimeIconName.Alarm -> MiuixIcons.Glass.Alarm
    RuntimeIconName.Phone -> MiuixIcons.Glass.Phone
    RuntimeIconName.ChevronRight -> MiuixIcons.Glass.ChevronForward
    RuntimeIconName.ChevronDown -> MiuixIcons.Glass.ExpandMore
    RuntimeIconName.ChevronUp -> MiuixIcons.Glass.ExpandLess
    RuntimeIconName.NavDashboard -> MiuixIcons.Glass.GridView
    RuntimeIconName.Trash -> MiuixIcons.Glass.Delete
    RuntimeIconName.Check -> MiuixIcons.Glass.Ok
    RuntimeIconName.Plus -> MiuixIcons.Glass.Add
    RuntimeIconName.List -> MiuixIcons.Glass.ListView
    RuntimeIconName.Palette -> MiuixIcons.Glass.Theme
    RuntimeIconName.FolderDownload -> MiuixIcons.Glass.FileDownloads
    RuntimeIconName.Visibility -> MiuixIcons.Glass.Show
    RuntimeIconName.VisibilityOff -> MiuixIcons.Glass.Hide
    RuntimeIconName.Mail -> MiuixIcons.Glass.Email
    RuntimeIconName.History -> MiuixIcons.Glass.RecentTasks
    RuntimeIconName.Checklist -> MiuixIcons.Glass.Tasks
    RuntimeIconName.Reverse -> MiuixIcons.Glass.Undo
    RuntimeIconName.Menu, RuntimeIconName.PanelLeftClose -> MiuixIcons.Glass.Sidebar
    else -> null
}
