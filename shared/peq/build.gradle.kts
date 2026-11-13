plugins { alias(libs.plugins.android.library) }

apply(from = "devices.gradle.kts")

android {
    namespace = "com.pxr.cymatic.peq"
    buildFeatures.compose = false
    sourceSets.named("main") {
        kotlin.directories += layout.buildDirectory.dir("generated/devices/kotlin").get().asFile.path
    }
}

tasks.named("preBuild") { dependsOn("generateDeviceCatalog") }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}

