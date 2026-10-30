pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.7"
    // Picks fabric-loom-remap for obfuscated Minecraft versions and fabric-loom for 26+.
    // The Loom version comes from `loomx.loom_version` in gradle.properties.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "lias-media-player"

// Plain Java modules: compiled once, no Minecraft on their classpath.
include("api", "core", "ui")

// An addon, built the way one outside the repository would be: against the API alone.
include("example-addon")
project(":example-addon").projectDir = file("examples/example-addon")

// The only Minecraft- and loader-specific code. Stonecutter builds one subproject per
// Minecraft version and loader (platform/versions/<mc>-<loader>), all sharing platform/src.
// This is the only place the matrix is declared.
include("platform")
stonecutter {
    create(":platform") {
        fun match(version: String, vararg loaders: String) = loaders.forEach {
            version("$version-$it", version).buildscript("build.$it.gradle.kts")
        }

        match("1.21.1", "neoforge", "fabric")

        vcsVersion = "1.21.1-neoforge"
    }
}
