plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

group = "com.zeerqi27.etoilebridge"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.charleskorn.kaml:kaml:0.60.0")
    implementation("com.github.freeze-dolphin:aff-compose:v3.1.5")
    compileOnly("org.jetbrains:annotations:24.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    val localTmp = layout.buildDirectory.dir("tmp/test-tmp")
    doFirst {
        localTmp.get().asFile.mkdirs()
    }
    systemProperty("java.io.tmpdir", localTmp.get().asFile.absolutePath)
}
