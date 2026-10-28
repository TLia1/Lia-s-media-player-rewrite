plugins {
    id("lmp.java-module")
}

description = "The mod's own UI toolkit, windows and screens, drawn through a Canvas. No Minecraft."

dependencies {
    api(project(":core"))
    compileOnly(libs.slf4j.api)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.slf4j.simple)
}
