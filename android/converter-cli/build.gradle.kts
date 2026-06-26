import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    application
}

group = "com.zeerqi27.etoilebridge"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":converter-core"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.zeerqi27.etoilebridge.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
    val localTmp = layout.buildDirectory.dir("tmp/test-tmp")
    doFirst {
        localTmp.get().asFile.mkdirs()
    }
    systemProperty("java.io.tmpdir", localTmp.get().asFile.absolutePath)
}

tasks.jar {
    archiveClassifier.set("thin")
    manifest {
        attributes["Main-Class"] = "com.zeerqi27.etoilebridge.cli.MainKt"
    }
}

tasks.register<Jar>("fatJar") {
    archiveFileName.set("converter-cli.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.zeerqi27.etoilebridge.cli.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
}
