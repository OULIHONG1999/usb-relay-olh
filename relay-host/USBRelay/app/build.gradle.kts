plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Read version from version.properties
import java.util.Properties
import java.io.FileInputStream

val versionPropsFile = file("../version.properties")
var versionMajor = 1
var versionMinor = 0
var versionPatch = 0

if (versionPropsFile.exists()) {
    val props = Properties()
    FileInputStream(versionPropsFile).use { props.load(it) }
    versionMajor = props.getProperty("VERSION_MAJOR", "1").toInt()
    versionMinor = props.getProperty("VERSION_MINOR", "0").toInt()
    versionPatch = props.getProperty("VERSION_PATCH", "0").toInt()
}

val versionName = "$versionMajor.$versionMinor.$versionPatch"
val versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch

android {
    namespace = "com.olh.usbrelay"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.olh.usbrelay"
        minSdk = 30
        targetSdk = 36
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Make version available to the app
        buildConfigField("int", "VERSION_CODE", "${versionMajor * 10000 + versionMinor * 100 + versionPatch}")
        buildConfigField("String", "VERSION_NAME", "\"$versionMajor.$versionMinor.$versionPatch\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}