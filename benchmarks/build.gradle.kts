plugins {
    java
}

description = "JMH benchmarks of the hot paths, against the budget in REWRITE-DESIGN.md §12. Not shipped."

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(testFixtures(project(":core")))
    implementation(libs.jmh.core)
    implementation(libs.gson)
    implementation(libs.slf4j.api)
    compileOnly(libs.jspecify)
    annotationProcessor(libs.jmh.generator)
    runtimeOnly(libs.slf4j.simple)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

// ./gradlew :benchmarks:jmh                       every benchmark, a few minutes
// ./gradlew :benchmarks:jmh -Pjmh.include=Chat     those whose name matches
val include = providers.gradleProperty("jmh.include").orElse("")
val results = layout.buildDirectory.file("jmh-results.json")
tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "Runs the JMH benchmarks against the performance budget."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("-f", "1", "-wi", "3", "-w", "1s", "-i", "5", "-r", "1s",
            "-rf", "json", "-rff", results.get().asFile.absolutePath) +
            listOfNotNull(include.get().ifEmpty { null })
    })
}

// ./gradlew :benchmarks:startBudget                      a generated 720p clip over loopback
// ./gradlew :benchmarks:startBudget --args="<link>"      a real link (needs the network)
tasks.register<JavaExec>("startBudget") {
    group = "verification"
    description = "Times a start and a seek through the whole engine (see StartBudget)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.lia.mediaplayer.benchmarks.StartBudget"
}
