plugins {
    alias(libs.plugins.android.library)
}

android {
    buildFeatures.compose = false
    namespace = "com.pxr.cymatic.usb"
    ndkVersion = "29.0.14206865"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild.cmake {
            cppFlags += listOf("-std=c++17", "-Wall", "-Wextra", "-Werror")
        }
    }
    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
