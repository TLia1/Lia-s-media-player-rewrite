plugins {
    id("dev.kikugie.stonecutter")
    // Applies fabric-loom-remap for obfuscated Minecraft versions and fabric-loom for 26+.
    id("dev.kikugie.loom-back-compat")
    // Applied below, once Stonecutter has loaded the properties it reads.
    id("lmp.platform-common") apply false
}

// The subproject is "<mc>-fabric"; declaring the Minecraft version as the property tag
// makes the ["<mc>"] tables of stonecutter.properties.toml apply to it.
stonecutter.properties.tags(stonecutter.current.version)
apply(plugin = "lmp.platform-common")

fun prop(name: String): String = project.property(name).toString()

val modId = prop("mod.id")
val minecraftVersion = prop("deps.minecraft")

version = prop("mod.version")
group = prop("mod.group")

base {
    archivesName = "$modId-$minecraftVersion-fabric"
}

sourceSets.main {
    java.exclude("**/platform/neoforge/**")
}

repositories {
    maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    // Mojang's names, so platform/src reads the same as on NeoForge. No-op on 26+.
    the<dev.kikugie.loomx.LoomCompatDependencyExtension>().applyMojangMappings()

    "modImplementation"("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    "modCompileOnly"("com.terraformersmc:modmenu:${prop("deps.modmenu")}")

    // In the dev client the plain modules are ordinary libraries on the classpath; in
    // the released jar their classes are merged in (see lmp.platform-common).
    runtimeOnly(project(":ui"))

    // `-Plmp.exampleAddon` puts the example addon in the dev client: Fabric finds it by the
    // fabric.mod.json in its jar, as it would in the mods folder.
    if (providers.gradleProperty("lmp.exampleAddon").isPresent) {
        runtimeOnly(project(":example-addon"))
    }
}

tasks.withType<ProcessResources>().configureEach {
    val replaceProperties = mapOf(
        "mod_id" to modId,
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "mod_license" to prop("mod.license"),
        "api_version" to prop("mod.api_version"),
        "fabric_loader_version" to prop("deps.fabric_loader"),
        "minecraft_version_range_fabric" to prop("deps.minecraft_range_fabric"),
        "java_version" to prop("deps.java"),
    )
    inputs.properties(replaceProperties)

    exclude("META-INF/neoforge.mods.toml")

    filesMatching(listOf("fabric.mod.json", "liasmediaplayer.mixins.json")) {
        expand(replaceProperties)
    }
}
