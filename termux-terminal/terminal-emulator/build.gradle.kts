plugins {
    id("com.android.library")
}

val libraryCompileSdk = (findProperty("butlerCompileSdk") as String?)?.toIntOrNull() ?: 36

android {
    namespace = "com.termux.emulator"
    compileSdk = libraryCompileSdk

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
