import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val compileSdkValue = (((findProperty("butlerCompileSdk") as String?)?.toIntOrNull() ?: 37).coerceAtLeast(37))
val targetSdkValue = (findProperty("butlerTargetSdk") as String?)?.toIntOrNull() ?: compileSdkValue

android {
    namespace = "io.github.dryearth.Nanaya"
    compileSdk = compileSdkValue

    defaultConfig {
        applicationId = "io.github.dryearth.Nanaya"
        minSdk = 33
        targetSdk = targetSdkValue
        versionCode = 156
        versionName = "0.8.59"
        vectorDrawables { useSupportLibrary = true }
        resValue("string", "app_name", "Nanaya")
        ndk {
            // Conscrypt/libspake2 ship prebuilt JNI libs. Modern LineageOS 24
            // devices are arm64; keep the APK small by not bundling x86/32-bit.
            abiFilters += listOf("arm64-v8a")
        }
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
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
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
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4")
    implementation("io.github.dokar3:quickjs-kt:1.0.18")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
