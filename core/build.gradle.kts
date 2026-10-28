plugins {
    id("lmp.java-module")
    `java-test-fixtures`
}

description = "The engine: links, resolution, tools, playback, library, settings. No Minecraft."

dependencies {
    api(project(":api"))
    // Provided by Minecraft at runtime.
    compileOnly(libs.slf4j.api)

    testFixturesCompileOnly(libs.jspecify)

    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)
}
