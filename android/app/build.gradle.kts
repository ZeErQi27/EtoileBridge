import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseSigningPropertiesFile =
    file("E:/ArcpkgAPP/secrets/EtoileBridge/android-signing.properties")
val releaseSigningProperties = Properties()
val hasReleaseSigningProperties = releaseSigningPropertiesFile.isFile
if (hasReleaseSigningProperties) {
    releaseSigningPropertiesFile.inputStream().use {
        releaseSigningProperties.load(it)
    }
}

fun releaseSigningValue(key: String): String =
    releaseSigningProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Missing Android release signing property: $key")

fun releaseStoreFile(): File {
    val configured = releaseSigningValue("storeFile")
    val configuredFile = File(configured)
    return if (configuredFile.isAbsolute) {
        configuredFile
    } else {
        File(releaseSigningPropertiesFile.parentFile, configured)
    }
}

android {
    namespace = "com.zeerqi27.etoile_bridge"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.zeerqi27.etoile_bridge"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (hasReleaseSigningProperties) {
            create("release") {
                storeFile = releaseStoreFile()
                storePassword = releaseSigningValue("storePassword")
                keyAlias = releaseSigningValue("keyAlias")
                keyPassword = releaseSigningValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigningProperties) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation(project(":converter-core"))
    implementation("androidx.documentfile:documentfile:1.0.1")
}
