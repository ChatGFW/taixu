plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.custom_iteration"
}

dependencies {
    // TaiXuDevBuildCoordinator 通过 LinuxRuntime 标准契约在沙盒内执行 gh 命令
    implementation(project(":runtime"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test.robolectric)
}
