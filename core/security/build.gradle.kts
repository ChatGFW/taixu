plugins {
    alias(libs.plugins.taixu.android.library)
}

android {
    namespace = "top.wkbin.taixu.core.security"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.koin.core)
    testImplementation(libs.junit)
}
