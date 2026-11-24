plugins {
    id("com.android.library")
}

val libraryCompileSdk = (findProperty("butlerCompileSdk") as String?)?.toIntOrNull() ?: 36

android {
    namespace = "com.termux.view"
    compileSdk = libraryCompileSdk

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    api(project(":termux-terminal:terminal-emulator"))
    testImplementation("junit:junit:4.13.2")
}
