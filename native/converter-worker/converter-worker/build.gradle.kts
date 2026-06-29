plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "com.zeerqi27.etoilebridge.flutter"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":converter-core"))
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

application {
    mainClass.set("com.zeerqi27.etoilebridge.flutter.worker.FlutterWorkerMainKt")
}
