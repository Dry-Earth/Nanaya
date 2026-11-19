import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val compileSdkValue = (findProperty("butlerCompileSdk") as String?)?.toIntOrNull() ?: 36
val targetSdkValue = (findProperty("butlerTargetSdk") as String?)?.toIntOrNull() ?: compileSdkValue

android {
    namespace = "io.github.dryearth.solidbutler"
    compileSdk = compileSdkValue

    defaultConfig {
        applicationId = "io.github.dryearth.solidbutler"
        minSdk = 31
        targetSdk = targetSdkValue
        versionCode = 24
        versionName = "0.7.15"
        vectorDrawables { useSupportLibrary = true }
        resValue("string", "app_name", "汐")
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("BUTLER_STORE_FILE") ?: rootProject.file("signing/release.jks").absolutePath
            val store = file(storePath)
            if (store.exists()) {
                storeFile = store
                storePassword = System.getenv("BUTLER_STORE_PASSWORD") ?: "Butler2026!"
                keyAlias = System.getenv("BUTLER_KEY_ALIAS") ?: "butler"
                keyPassword = System.getenv("BUTLER_KEY_PASSWORD") ?: "Butler2026!"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
