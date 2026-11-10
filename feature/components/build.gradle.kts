plugins {
    alias(libs.plugins.taixu.android.library.compose)
}

android {
    namespace = "top.wkbin.taixu.feature.components"
}

dependencies {
    implementation(project(":core:model"))
    api(project(":core:markdown"))
    api(project(":feature:theme"))
    implementation(libs.bundles.compose.ui)
    api(libs.miuix.ui)
    api(libs.miuix.preference)
    api(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.coil)
    testImplementation(libs.junit)
}
