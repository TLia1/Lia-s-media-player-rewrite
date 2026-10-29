plugins {
    id("lmp.java-module")
}

description = "The mod's own UI toolkit, windows and screens, drawn through a Canvas. No Minecraft."

dependencies {
    api(project(":core"))
    compileOnly(libs.slf4j.api)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.slf4j.api)
    testImplementation(libs.gson)
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    // The language files ship with the platform layer, which builds them into every jar; the
    // UI's tests check that they hold exactly the keys the code uses.
    val langDir = rootProject.layout.projectDirectory.dir("platform/src/main/resources/assets/liasmediaplayer/lang")
    inputs.dir(langDir)
    systemProperty("liasmediaplayer.langDir", langDir.asFile.absolutePath)
    // The integration test plays generated media through real ffmpeg sessions.
    maxParallelForks = 1
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
}
