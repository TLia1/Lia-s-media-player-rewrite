package com.lia.mediaplayer.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Inside the platform layer, only the loader packages know their loader. The Minecraft
 * adapters in {@code platform.mc} are shared by both and must stay loader-neutral.
 */
@AnalyzeClasses(packages = "com.lia.mediaplayer.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class PlatformLayeringTest {

    @ArchTest
    static final ArchRule MC_ADAPTERS_ARE_LOADER_NEUTRAL = noClasses()
            .that()
            .resideInAPackage("com.lia.mediaplayer.platform.mc..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "net.fabricmc..", "net.neoforged..", "com.lia.mediaplayer.platform.fabric..",
                    "com.lia.mediaplayer.platform.neoforge..");

    // Each target compiles only its own loader's package, so on a Fabric target the NeoForge
    // rule has no classes to check (and the other way round). That is expected, not a failure.

    @ArchTest
    static final ArchRule FABRIC_DOES_NOT_SEE_NEOFORGE = noClasses()
            .that()
            .resideInAPackage("com.lia.mediaplayer.platform.fabric..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.neoforged..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule NEOFORGE_DOES_NOT_SEE_FABRIC = noClasses()
            .that()
            .resideInAPackage("com.lia.mediaplayer.platform.neoforge..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.fabricmc..")
            .allowEmptyShould(true);
}
