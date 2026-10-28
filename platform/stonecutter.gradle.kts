plugins {
    id("dev.kikugie.stonecutter")
}

// Which target platform/src is currently switched to. Rewritten by the
// `Set active project to <mc>-<loader>` tasks — do not edit by hand, and run
// `Reset active project` before committing.
stonecutter active "1.21.1-neoforge" /* [SC] DO NOT EDIT */

stonecutter parameters {
    // `//? if fabric { … }` / `//? if neoforge { … }` for the rare line inside a shared
    // platform/mc file. Whole loader-specific files live in platform/<loader>/ instead and
    // each buildscript excludes the other loader's package from compilation.
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")
}

// The per-target `build` tasks share platform/src, so they must not run concurrently.
stonecutter tasks {
    order("build")
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds the mod for every Minecraft version and loader."
    dependsOn(stonecutter.versions.map { ":platform:${it.project}:build" })
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs the platform tests for every Minecraft version and loader."
    dependsOn(stonecutter.versions.map { ":platform:${it.project}:test" })
}
