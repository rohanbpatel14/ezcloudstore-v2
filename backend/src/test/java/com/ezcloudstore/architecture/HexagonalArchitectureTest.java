package com.ezcloudstore.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Enforces the hexagonal (ports & adapters) dependency rule:
 * domain depends on nothing, application depends only on domain,
 * adapters depend inward and never on each other.
 *
 * Written as plain JUnit tests (not @ArchTest fields) so the rules
 * are guaranteed to execute under Surefire's Jupiter engine.
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.ezcloudstore");
    }

    @Test
    void hexagonalLayersAreRespected() {
        onionArchitecture()
                .domainModels("com.ezcloudstore.domain.model..")
                .domainServices("com.ezcloudstore.domain.port..")
                .applicationServices("com.ezcloudstore.application..")
                .adapter("rest", "com.ezcloudstore.adapters.in.rest..")
                .adapter("dynamodb", "com.ezcloudstore.adapters.out.dynamodb..")
                .adapter("s3", "com.ezcloudstore.adapters.out.s3..")
                .adapter("id", "com.ezcloudstore.adapters.out.id..")
                .withOptionalLayers(true)
                .ignoreDependency(
                        com.tngtech.archunit.base.DescribedPredicate.describe("wiring config",
                                javaClass -> javaClass.getPackageName().startsWith("com.ezcloudstore.config")),
                        com.tngtech.archunit.base.DescribedPredicate.alwaysTrue())
                .check(classes);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("com.ezcloudstore.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "software.amazon..",
                        "com.amazonaws..",
                        "jakarta.persistence..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void applicationIsAwsFree() {
        noClasses()
                .that().resideInAPackage("com.ezcloudstore.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "software.amazon..",
                        "com.amazonaws..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
