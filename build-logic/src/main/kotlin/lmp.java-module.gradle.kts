// Conventions for the plain Java modules (api, core, ui): compiled once for Java 21,
// with no Minecraft on the classpath, and held to the strictest checks in the build.

import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    jacoco
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()
fun version(alias: String) = libs.findVersion(alias).get().requiredVersion

group = "com.lia.mediaplayer"

java {
    withSourcesJar()
}

dependencies {
    // Annotations only: missing annotation classes are ignored at runtime.
    "compileOnlyApi"(lib("jspecify"))
    "errorprone"(lib("errorprone-core"))
    "errorprone"(lib("nullaway"))

    testImplementation(platform(lib("junit-bom")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj"))
    testImplementation(lib("archunit"))
    testCompileOnly(lib("jspecify"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
    // --release rather than a toolchain: the bytecode must run on Java 21 (Minecraft
    // 1.21.x) and Java 25 (26.x), and --release also restricts the JDK API to 21's.
    options.release = version("java-release").toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", if (name.contains("Test")) CheckSeverity.OFF else CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

jacoco {
    toolVersion = version("jacoco")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestReport", "jacocoTestCoverageVerification")
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(version("palantir-java-format"))
        removeUnusedImports()
        forbidWildcardImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
