plugins {
    base
}

// ---------------------------------------------------------------------------
// Version guards stay in the platform layer
// ---------------------------------------------------------------------------
// Stonecutter's `//?` comments are how version-specific lines are written, and the design
// confines them to platform/. One appearing in api, core or ui means version-specific code
// leaked into a layer that is compiled once for every target — and would be silently
// ignored there, since only platform/ is preprocessed.
val checkVersionGuards by tasks.registering {
    group = "verification"
    description = "Fails if a Stonecutter //? guard appears outside platform/."

    val sources = files("api/src", "core/src", "ui/src").asFileTree.matching { include("**/*.java", "**/*.kt") }
    inputs.files(sources)

    doLast {
        val guard = Regex("""//\?""")
        val offenders = sources.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (guard.containsMatchIn(line)) "${file.relativeTo(rootDir)}:${index + 1}" else null
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Stonecutter guards are only allowed under platform/. Found:\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
    }
}

tasks.check {
    dependsOn(checkVersionGuards)
}

// ---------------------------------------------------------------------------
// Aggregates
// ---------------------------------------------------------------------------
tasks.register("verifyModules") {
    group = "verification"
    description = "Checks the plain Java modules: tests, coverage, layering, formatting, guards."
    dependsOn(":api:check", ":core:check", ":ui:check", checkVersionGuards)
}
