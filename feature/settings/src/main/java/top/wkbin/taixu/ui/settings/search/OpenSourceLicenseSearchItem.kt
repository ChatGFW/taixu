package top.wkbin.taixu.ui.settings.search

import top.wkbin.taixu.ui.components.RuntimeIconName

internal val openSourceLicenseSearchItem = SettingsSearchItem(
    id = "about_licenses",
    title = "开源许可与第三方许可证",
    subtitle = "查看本应用 GPL-3.0 与随包第三方组件许可证全文",
    category = SettingsSearchCategory.ABOUT,
    target = SettingsSearchTarget.ABOUT_LICENSES,
    icon = RuntimeIconName.Document,
    keywords = listOf(
        "license", "licences", "gpl", "apache", "oss", "open source",
        "开源许可", "开源协议", "许可证", "第三方", "xuke", "xieyi",
    ),
    breadcrumb = "关于与官方社区 > 开源许可",
)
