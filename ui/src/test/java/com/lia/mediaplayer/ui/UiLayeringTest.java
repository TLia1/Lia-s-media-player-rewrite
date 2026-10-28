package com.lia.mediaplayer.ui;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The UI draws through a Canvas and never touches the game or the platform layer. */
@AnalyzeClasses(packages = "com.lia.mediaplayer.ui", importOptions = ImportOption.DoNotIncludeTests.class)
class UiLayeringTest {

    @ArchTest
    static final ArchRule NO_GAME_OR_LOADER = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("net.minecraft..", "com.mojang..", "net.fabricmc..", "net.neoforged..", "org.lwjgl..");

    @ArchTest
    static final ArchRule NO_PLATFORM =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("com.lia.mediaplayer.platform..");
}
