import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val androidSigningProperties = Properties()
val androidSigningPropertiesFile = providers.environmentVariable("ETOILEBRIDGE_ANDROID_SIGNING_PROPERTIES")
    .orNull
    ?.let(::file)
    ?: rootProject.projectDir.parentFile.resolve("secrets/EtoileBridge/android-signing.properties")
if (androidSigningPropertiesFile.isFile) {
    androidSigningPropertiesFile.inputStream().use(androidSigningProperties::load)
}

fun signingValue(envName: String, propertyName: String): String? =
    providers.environmentVariable(envName).orNull
        ?: androidSigningProperties.getProperty(propertyName)

val releaseStoreFile = signingValue("ETOILEBRIDGE_ANDROID_KEYSTORE", "storeFile")
val releaseStorePassword = signingValue("ETOILEBRIDGE_ANDROID_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ETOILEBRIDGE_ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ETOILEBRIDGE_ANDROID_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.zeerqi27.etoilebridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zeerqi27.etoilebridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 26626
        versionName = "1.2.26626"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":converter-core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
