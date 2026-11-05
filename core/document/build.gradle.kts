plugins { alias(libs.plugins.taixu.android.library) }
android { namespace = "top.wkbin.taixu.core.document" }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.bundles.test.robolectric)
}
