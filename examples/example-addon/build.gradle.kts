// An addon of Lia's Media Player, built as an addon outside this repository would be: a plain
// Java library against the API, with no Minecraft and no loader plugin. One jar serves both
// loaders: Fabric reads fabric.mod.json and calls the entrypoint, NeoForge reads
// neoforge.mods.toml and constructs the @Mod class, and neither looks at the other's files.

plugins {
    `java-library`
}

group = "com.example"
version = "1.0.0"
description = "Example addon of Lia's Media Player"

base {
    archivesName = "exampleaddon"
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
}

dependencies {
    // Outside this repository: compileOnly("com.lia.mediaplayer:liasmediaplayer-api:4.0.0").
    // The mod ships the API classes; an addon never bundles them.
    compileOnly(project(":api"))
    // Minecraft's logger; the game provides it.
    compileOnly(libs.slf4j.api)
    // The two annotations of the NeoForge entry class, and nothing else of NeoForge.
    compileOnly("net.neoforged.fancymodloader:loader:4.0.42") { isTransitive = false }
    compileOnly("net.neoforged:mergetool:2.0.0:api") { isTransitive = false }

    testImplementation(project(":api"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.withType<JavaCompile>().configureEach {
    // The oldest Java of the Minecraft versions the addon is for: 1.21.x runs on Java 21.
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
        expand(properties)
    }
}

tasks.test {
    useJUnitPlatform()
}
