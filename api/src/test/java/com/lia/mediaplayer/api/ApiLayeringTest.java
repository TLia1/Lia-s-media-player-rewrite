package com.lia.mediaplayer.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The API depends on the JDK and on itself, nothing else — no game, no loader, no internals. */
@AnalyzeClasses(packages = "com.lia.mediaplayer.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiLayeringTest {

    @ArchTest
    static final ArchRule ONLY_JDK_AND_ITSELF = classes()
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "org.jspecify..", "com.lia.mediaplayer.api..");
}
