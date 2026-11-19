# LineageOS health binder
-keep class lineageos.health.** { *; }


# Hidden API bypass
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# Keep line numbers for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Wireless ADB (libadb / conscrypt / spake2 / sun-security)
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.**
-dontwarn org.conscrypt.**
-dontwarn javax.naming.**
-keep class io.github.muntashirakon.adb.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.conscrypt.** { *; }
-keep class android.sun.security.** { *; }
-keep class io.github.muntashirakon.crypto.spake2.** { *; }
-keep class android.sun.misc.** { *; }
