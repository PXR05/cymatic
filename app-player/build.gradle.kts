plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.pxr.cymatic"
    defaultConfig {
        applicationId = "com.pxr.cymatic"
    }
    packaging {
        dex.useLegacyPackaging = true
        jniLibs.useLegacyPackaging = true
    }
}
dependencies {
    implementation(project(":shared:music"))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
