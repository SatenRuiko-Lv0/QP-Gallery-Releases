import java.io.File

plugins {
    id("com.android.application") version "9.4.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

val localAppData: String = System.getenv("LOCALAPPDATA") ?: ""
val sharedKeystore = File(localAppData, "QP-Gallery-Comic/comic-test.p12")

android {
    namespace = "com.qpgallery.glass"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.qpgallery.glass"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.0.0-glass.16"
    }

    signingConfigs {
        if (sharedKeystore.isFile) {
            create("release") {
                storeFile = sharedKeystore
                storePassword = "android"
                keyAlias = "qp-comic-test"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3")

    // Exact KernelSU v3.3.1 Miuix + Liquid Glass stack
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4")

    // Kyant Capsule G2 continuous rounded corners (Apple-grade continuous squircle curves)
    implementation("io.github.kyant0:capsule:2.1.1")
}
