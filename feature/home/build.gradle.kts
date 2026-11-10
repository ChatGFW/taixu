plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.home"
}

dependencies {
    implementation(project(":feature:theme"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(libs.androidx.activity.compose)
}
