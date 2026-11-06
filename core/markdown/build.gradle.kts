plugins { alias(libs.plugins.taixu.jvm.library) }

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
