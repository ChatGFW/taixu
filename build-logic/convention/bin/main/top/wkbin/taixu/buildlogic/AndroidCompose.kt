package top.wkbin.taixu.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension,
) {
    // Miuix 0.9.3 publishes an unused window-size dependency, and preview tooling
    // also brings in Material 3. Exclude these unused transitive artifacts.
    configurations.configureEach {
        exclude(mapOf("group" to "androidx.compose.material3"))
        exclude(mapOf("group" to "org.jetbrains.compose.material3"))
    }

    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }

        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            "implementation"(platform(bom))
            "androidTestImplementation"(platform(bom))
            "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
