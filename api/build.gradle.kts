plugins {
    id("lmp.java-module")
    `maven-publish`
}

description = "Public API of Lia's Media Player. Minecraft-free; the same jar works on every target."

// The API is MIT, unlike the rest of the mod (GPL-3.0): ship its notice in its own jars.
tasks.withType<Jar>().configureEach {
    from("LICENSE")
}

publishing {
    publications {
        register<MavenPublication>("api") {
            artifactId = "liasmediaplayer-api"
            from(components["java"])
            pom {
                name = "Lia's Media Player API"
                description = project.description
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit"
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(rootProject.layout.projectDirectory.dir("repo"))
        }
    }
}
