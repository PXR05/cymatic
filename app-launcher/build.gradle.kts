plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.pxr.cymatic.launcher"
    defaultConfig {
        applicationId = "com.pxr.cymatic.launcher"
    }
}
dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":shared:music"))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
