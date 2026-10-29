plugins {
    id("lmp.java-module")
    `java-test-fixtures`
}

description = "The engine: links, resolution, tools, playback, library, settings. No Minecraft."

dependencies {
    api(project(":api"))
    // Provided by Minecraft at runtime.
    compileOnly(libs.slf4j.api)
    compileOnly(libs.gson)

    testFixturesCompileOnly(libs.jspecify)

    testImplementation(libs.slf4j.api)
    testImplementation(libs.gson)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    // Integration tests (tag "integration") run real ffmpeg on media generated in the test,
    // and skip themselves when ffmpeg is not installed. Each starts processes and sockets,
    // so a few at a time is plenty.
    maxParallelForks = 1
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
}

// The CLI harness: plays a link through the real engine, real sound card included.
//   ./gradlew :core:playUrl --args="https://www.youtube.com/watch?v=…"
// Type `help` once it runs. Tools are found or downloaded into run/harness/liasmediaplayer/bin.
tasks.register<JavaExec>("playUrl") {
    group = "application"
    description = "Plays a link through the engine from the terminal (see PlayUrl)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.lia.mediaplayer.core.harness.PlayUrl"
    standardInput = System.`in`
    val gameDir = rootProject.layout.projectDirectory.dir("run/harness").asFile
    doFirst { gameDir.mkdirs() }
    systemProperty("liasmediaplayer.harness.gamedir", gameDir.absolutePath)
}
