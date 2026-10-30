plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.148"
    // Applied below, once Stonecutter has loaded the properties it reads.
    id("lmp.platform-common") apply false
}

// The subproject is "<mc>-neoforge"; declaring the Minecraft version as the property tag
// makes the ["<mc>"] tables of stonecutter.properties.toml apply to it.
stonecutter.properties.tags(stonecutter.current.version)
apply(plugin = "lmp.platform-common")

fun prop(name: String): String = project.property(name).toString()

val modId = prop("mod.id")
val minecraftVersion = prop("deps.minecraft")
val parchmentVersion = prop("deps.parchment")

version = prop("mod.version")
group = prop("mod.group")

base {
    archivesName = "$modId-$minecraftVersion-neoforge"
}

sourceSets.main {
    java.exclude("**/platform/fabric/**")
}

repositories {
    maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
}

@Suppress("UNCHECKED_CAST")
val bundledSourceSets = extra["bundledSourceSets"] as List<SourceSet>

// `-Plmp.exampleAddon` puts the example addon in the dev client, as a mod of its own.
val exampleAddon: SourceSet? = if (providers.gradleProperty("lmp.exampleAddon").isPresent) {
    evaluationDependsOn(":example-addon")
    project(":example-addon").extensions.getByType<SourceSetContainer>().named("main").get()
} else {
    null
}

dependencies {
    // The class folders themselves rather than the module jars: the `mods` block below
    // groups them with this project's classes into one mod. A jar of the same packages
    // beside them would be a second module and a split-package error at launch.
    runtimeOnly(files(bundledSourceSets.map { it.output }))
    exampleAddon?.let { runtimeOnly(files(it.output)) }
}

neoForge {
    version = prop("deps.neoforge")

    if (parchmentVersion.isNotEmpty()) {
        parchment {
            minecraftVersion = parchmentVersion.substringBefore(':')
            mappingsVersion = parchmentVersion.substringAfter(':')
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            bundledSourceSets.forEach { sourceSet(it) }
        }
        exampleAddon?.let { addon -> create("exampleaddon") { sourceSet(addon) } }
    }

    runs {
        create("client") {
            client()
            gameDirectory = layout.projectDirectory.dir("run/client")
        }
    }
}

tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "mod_id" to modId,
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "mod_license" to prop("mod.license"),
        "api_version" to prop("mod.api_version"),
        "api_license" to prop("mod.api_license"),
        "loader_version_range" to prop("mod.loader_version_range"),
        "neo_version" to prop("deps.neoforge"),
        "minecraft_version_range" to prop("deps.minecraft_range"),
        "java_version" to prop("deps.java"),
    )
    inputs.properties(replaceProperties)

    exclude("fabric.mod.json")

    filesMatching(listOf("META-INF/neoforge.mods.toml", "liasmediaplayer.mixins.json")) {
        expand(replaceProperties)
    }
}
