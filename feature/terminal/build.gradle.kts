plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.terminal"
}

dependencies {
    implementation(project(":feature:theme"))
    implementation(project(":plugin:ui"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    // Pair TerminalView with runtime's compatible fork; never package both emulator implementations.
    implementation(libs.termux.terminal.view) {
        exclude(group = "com.termux", module = "terminal-emulator")
    }
}
