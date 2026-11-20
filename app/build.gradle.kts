plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.pxr.cymatic"
    ndkVersion = "29.0.14206865"
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
    implementation(project(":shared:peq"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.guava)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
