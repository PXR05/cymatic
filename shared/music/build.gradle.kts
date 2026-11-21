plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pxr.cymatic.music"
    buildFeatures.compose = false
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    sourceSets.named("debug") {
        kotlin.directories += "build/generated/ksp/debug/kotlin"
    }
    sourceSets.named("release") {
        kotlin.directories += "build/generated/ksp/release/kotlin"
    }
}

dependencies {
    implementation(project(":shared:usb"))
    implementation(project(":shared:peq"))
    implementation(project(":shared:flac"))
    implementation(project(":shared:alac"))
    implementation(project(":shared:dsd"))
    configurations.configureEach {
        exclude(group = "com.intellij", module = "annotations")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.inspector)
    api(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    api(libs.androidx.media3.session)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.guava)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.orgjson)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
