package com.lia.mediaplayer.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Core is plain Java: no game, no loader, and nothing from the layers above it. */
@AnalyzeClasses(packages = "com.lia.mediaplayer.core", importOptions = ImportOption.DoNotIncludeTests.class)
class CoreLayeringTest {

    @ArchTest
    static final ArchRule NO_GAME_OR_LOADER = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.minecraft..", "com.mojang..", "net.fabricmc..", "net.neoforged..", "org.lwjgl..");

    @ArchTest
    static final ArchRule NO_UPPER_LAYERS = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.lia.mediaplayer.ui..", "com.lia.mediaplayer.platform..");
}
