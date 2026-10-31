plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.chat"
}

// Compose 截图用例依赖帧时钟与效果调度，同 JVM 连续运行会互相干扰，按测试类隔离。
tasks.withType<Test>().configureEach {
    forkEvery = 1
}

dependencies {
    implementation(project(":feature:theme"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(project(":harness"))
    implementation(project(":tools"))
    // A2UI PoC：render_surface 工具结果在聊天流内嵌渲染原生界面
    implementation(project(":feature:a2uipoc"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.haze)

    testImplementation(libs.bundles.test.robolectric)
}
