// Conventions shared by every platform subproject (<mc>-fabric, <mc>-neoforge), applied
// from platform/build.<loader>.gradle.kts after Stonecutter has loaded the properties.

plugins {
    `java-library`
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

/** The plain Java modules merged into the mod jar. They have no Minecraft dependency, so no remapping. */
val bundledModules = listOf(":api", ":core", ":ui")
bundledModules.forEach { evaluationDependsOn(it) }
val bundledOutputs = bundledModules.map { path ->
    project(path).extensions.getByType<SourceSetContainer>().named("main").get().output
}
extra["bundledSourceSets"] = bundledModules.map { path ->
    project(path).extensions.getByType<SourceSetContainer>().named("main").get()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(project.property("deps.java").toString().toInt())
}

dependencies {
    compileOnly(project(":ui"))
    compileOnly(lib("jspecify"))

    testImplementation(project(":ui"))
    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj"))
    testImplementation(lib("archunit"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // No -Werror here: a deprecation in a newly added Minecraft version should be a
    // warning to work through, not a failure across the whole matrix.
    options.compilerArgs.addAll(listOf("-Xlint:deprecation,unchecked,rawtypes,fallthrough"))
}

tasks.named<Jar>("jar") {
    bundledOutputs.forEach { from(it) }
    // The mod is GPL-3.0; the API classes merged in above are MIT and carry their own notice.
    from(rootProject.file("LICENSE"))
    from(rootProject.file("api/LICENSE")) { rename { "LICENSE-API" } }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
