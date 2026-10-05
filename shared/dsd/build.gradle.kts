plugins {
    alias(libs.plugins.android.library)
}

android {
    buildFeatures.compose = false
    namespace = "com.pxr.cymatic.dsd"
    ndkVersion = "29.0.14206865"
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
