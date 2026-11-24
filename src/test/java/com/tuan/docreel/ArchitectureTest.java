package com.tuan.docreel;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/** Luật phụ thuộc (PLAN 2.1). Luật chưa có class để kiểm vẫn chạy được nhờ {@code allowEmptyShould(true)}. */
@AnalyzeClasses(packages = "com.tuan.docreel", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.tuan.docreel";
    private static final String PROCESS_RUNNER = BASE + ".common.ProcessRunner";
    /** Lệnh CLI {@code --doctor}: in bảng ra stdout là chức năng chính của nó. */
    private static final String DOCTOR_COMMAND = BASE + ".system.DoctorCommand";

    @ArchTest
    static final ArchRule featurePackagesHaveNoCycles =
            slices().matching(BASE + ".(*)..").should().beFreeOfCycles().allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .because("Controller chỉ gọi *Service")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule pipelineDoesNotDependOnWebLayer = noClasses()
            .that()
            .resideInAPackage(BASE + ".pipeline..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Controller")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..", BASE + ".web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule scriptDependsOnlyOnCommon = noClasses()
            .that()
            .resideInAPackage(BASE + ".script..")
            .should()
            .dependOnClassesThat(
                    resideInAPackage(BASE + "..").and(not(resideInAnyPackage(BASE + ".script..", BASE + ".common.."))))
            .because("script là lõi thuần, chỉ được dùng common")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule onlyProcessRunnerUsesProcessBuilder = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(PROCESS_RUNNER)
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(ProcessBuilder.class, Runtime.class)
            .because("mọi lệnh ngoài phải đi qua common.ProcessRunner (timeout, kill, log)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noStandardStreams = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(DOCTOR_COMMAND)
            .should(GeneralCodingRules.ACCESS_STANDARD_STREAMS)
            .because("dùng SLF4J logger");

    @ArchTest
    static final ArchRule noPrintStackTrace =
            noClasses().should().callMethod(Throwable.class, "printStackTrace").because("dùng logger.error(msg, e)");
}
