plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.pxr.cymatic.music"
    // KSP's generated Kotlin sources are not reliably registered with AGP 9's
    // built-in Kotlin compiler. Register them through the supported Android
    // source-set DSL so Room implementations are always packaged.
    sourceSets.named("debug") {
        kotlin.directories += "build/generated/ksp/debug/kotlin"
    }
    sourceSets.named("release") {
        kotlin.directories += "build/generated/ksp/release/kotlin"
    }
}
dependencies {
    api(project(":shared:ui"))
    configurations.configureEach {
        exclude(group = "com.intellij", module = "annotations")
    }

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    api(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    api(libs.androidx.media3.session)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.activity.compose)
    api(libs.coil.compose)
    api(libs.androidx.navigation.compose)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.guava)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
