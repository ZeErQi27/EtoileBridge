plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

group = "com.zeerqi27.etoilebridge.electron"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":converter-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.zeerqi27.etoilebridge.electron.worker.WorkerMainKt")
}

tasks.test {
    useJUnitPlatform()
}
