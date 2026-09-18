import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    fun configureAndroid() {
        extensions.configure<CommonExtension>("android") {
            compileSdk { version = release(36) { minorApiLevel = 1 } }
            defaultConfig.minSdk = 31
            compileOptions.sourceCompatibility = JavaVersion.VERSION_11
            compileOptions.targetCompatibility = JavaVersion.VERSION_11
            buildFeatures.compose = true
        }
    }
    pluginManager.withPlugin("com.android.library") { configureAndroid() }
    pluginManager.withPlugin("com.android.application") {
        configureAndroid()
        extensions.configure<ApplicationExtension> {
            defaultConfig {
                targetSdk = 36
                versionCode = providers.gradleProperty("cymaticVersionCode").get().toInt()
                versionName = providers.gradleProperty("cymaticVersionName").get()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildTypes {
                release {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                }
            }
        }
    }
}
