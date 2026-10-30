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

    // Pure type renames, rewritten when a target is generated rather than guarded at every use.
    // Anything that changes a signature or the shape of a call is a guard instead. Each pair
    // round-trips: switching back to an older target restores the old name.
    //
    // 1.21.11 took Mojang's names for two types: ResourceLocation is Identifier, and Util moved
    // to net.minecraft.util.
    replacements.string(eval(current.version, ">=1.21.11")) {
        replace("ResourceLocation", "Identifier")
        replace("net.minecraft.Util;", "net.minecraft.util.Util;")
    }
    // 26.1 extracts the GUI rather than drawing it, and renamed the graphics type to match.
    // NeoForge kept the getter's old name (`getGuiGraphics()`, returning the new type), so the
    // getter is left alone.
    replacements.regex(eval(current.version, ">=26.1")) {
        replace("(?<!get)GuiGraphics(?!Extractor)", "GuiGraphicsExtractor", "GuiGraphicsExtractor", "GuiGraphics")
    }
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
