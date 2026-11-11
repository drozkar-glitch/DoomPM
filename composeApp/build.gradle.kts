import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.13.1")
        }
    }
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val dynamicVersionCode = if (ciRunNumber != null) ciRunNumber + 100 else 101
val dynamicVersionName = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0"

android {
    namespace = "cz.mares.doompm"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.doompm"
        minSdk = 24
        targetSdk = 36
        versionCode = dynamicVersionCode
        versionName = dynamicVersionName
    }

    signingConfigs {
        val keystorePath = System.getenv("CM_KEYSTORE_PATH")
        val keystorePassword = System.getenv("CM_KEYSTORE_PASSWORD")
        val keyAlias = System.getenv("CM_KEY_ALIAS")
        val keyPassword = System.getenv("CM_KEY_PASSWORD")

        if (!keystorePath.isNullOrBlank() && File(keystorePath).exists()) {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
        getByName("debug") {
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
