plugins {
    alias(libs.plugins.taixu.android.library.compose)
}

android {
    namespace = "top.wkbin.taixu.plugin.ui"
}

dependencies {
    api(project(":plugin:api"))
    api(project(":plugin:runtime"))
    implementation(project(":feature:components"))
    implementation(libs.bundles.compose.ui)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
