import com.pxr.cymatic.buildlogic.GenerateDeviceCatalog

plugins { alias(libs.plugins.android.library) }

tasks.register<GenerateDeviceCatalog>("generateDeviceCatalog") {
    group = "build"
    description = "Generates the DAC catalog from device records"
    records.from(fileTree("devices") { include("*.json") })
    outputDir.set(layout.buildDirectory.dir("generated/devices/kotlin"))
}

android {
    namespace = "com.pxr.cymatic.peq"
    buildFeatures.compose = false
    sourceSets.named("main") {
        kotlin.directories +=
            layout.buildDirectory.dir("generated/devices/kotlin").get().asFile.path
    }
}

tasks.named("preBuild") { dependsOn("generateDeviceCatalog") }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
