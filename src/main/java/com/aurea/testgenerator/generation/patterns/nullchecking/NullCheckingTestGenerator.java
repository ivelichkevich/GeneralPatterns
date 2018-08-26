package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.aurea.testgenerator.generation.patterns.nullchecking.NullCheckingTestTypes.NULL_CHECKING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.aurea.testgenerator.ast.Callability;
import com.aurea.testgenerator.generation.TestGenerator;
import com.aurea.testgenerator.generation.TestGeneratorResult;
import com.aurea.testgenerator.reporting.CoverageReporter;
import com.aurea.testgenerator.reporting.TestGeneratorResultReporter;
import com.aurea.testgenerator.source.Unit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * not supported: Stream.of(latitude, longitude).forEach(Objects::requireNonNull); final class as method argument (cant
 * * generate not nul value) changed arg name (eg by = to other reference) invocations of requireNonNull for not args
 * notimplemented by requirements requireNonNull method wrapper
 *
 * primitive types ignored
 */
@Component
@Profile("null-checking")
public class NullCheckingTestGenerator implements TestGenerator {

    private static Logger logger = LogManager.getLogger(NullCheckingTestGenerator.class);
    private static final String CALL_PATTERTN = "requireNonNull";
    private static final String IMPORT_PATTERTN = "java.util.";

    @Autowired
    private TestGeneratorResultReporter reporter;

    @Autowired
    private CoverageReporter coverageReporter;

    @Autowired
    NullCheckingMethodTestBuilder methodBuilder;

    @Autowired
    NullCheckingConstructorTestBuilder constructorBuilder;

    @Override
    public Collection<TestGeneratorResult> generate(Unit unit) {
        if (!importsMatch(unit)) {
            return Collections.EMPTY_LIST;
        }

        List<TestGeneratorResult> tests = new ArrayList<>();
        extractClasses(unit).stream().filter(this::classMatch).forEach(classDeclaration -> {
            List<CallableDeclaration> toPublish = new ArrayList<>();
            TestGeneratorResult result = generateTests(classDeclaration, toPublish);
            if (!result.getTests().isEmpty()) {
                tests.add(result);
                publishAndAdd(result, unit, toPublish);
            }
        });
        return tests;
    }

    private TestGeneratorResult generateTests(ClassOrInterfaceDeclaration classDeclaration,
            List<CallableDeclaration> toPublish) {
        TestGeneratorResult result = new TestGeneratorResult();
        result.setType(NULL_CHECKING);
        generateMethodTests(classDeclaration, toPublish, result);
        generateConstructorTests(classDeclaration, toPublish, result);
        return result;
    }

    private void generateMethodTests(ClassOrInterfaceDeclaration classDeclaration, List<CallableDeclaration> toPublish,
            TestGeneratorResult result) {
        classDeclaration.getMethods().stream().filter(this::methodMatch)
                .forEach(m -> buildCallable(toPublish, result, m, methodBuilder));
    }

    private void generateConstructorTests(ClassOrInterfaceDeclaration classDeclaration,
            List<CallableDeclaration> toPublish, TestGeneratorResult result) {
        classDeclaration.getConstructors().stream().filter(this::constructorMatch)
                .forEach(c -> buildCallable(toPublish, result, c, constructorBuilder));
    }

    private void buildCallable(List<CallableDeclaration> toPublish, TestGeneratorResult result,
            CallableDeclaration callableDeclaration, NullCheckingTestBuilder builder) {
        new NullCheckingCheckingService(callableDeclaration).createConfigs().forEach(v -> {
            builder.build(v).ifPresent(o -> result.getTests().add(o));
            toPublish.add(callableDeclaration);
        });
    }

    private boolean importsMatch(Unit unit) {
        return unit.getCu().getImports().stream().map(ImportDeclaration::getName).anyMatch(n -> n.asString()
                .startsWith(IMPORT_PATTERTN));
    }

    private boolean classMatch(ClassOrInterfaceDeclaration classDeclaration) {
        return Callability.isInstantiable(classDeclaration) && classDeclaration.toString().contains(CALL_PATTERTN);
    }

    private boolean constructorMatch(ConstructorDeclaration constructorDeclaration) {
        return !constructorDeclaration.getParameters().isEmpty() && !constructorDeclaration.getBody().isEmpty();
    }

    private boolean methodMatch(MethodDeclaration methodDeclaration) {
        return !methodDeclaration.getParameters().isEmpty() && methodDeclaration.getBody().isPresent()
                && !methodDeclaration.getBody().get().isEmpty();
    }

    private List<ClassOrInterfaceDeclaration> extractClasses(Unit unit) {
        return unit.getCu().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface()).collect(toList());
    }

    private void publishAndAdd(TestGeneratorResult testGeneratorResult, Unit unit,
            List<CallableDeclaration> testedMethods) {
        reporter.publish(testGeneratorResult, unit, testedMethods);
        coverageReporter.report(unit, testGeneratorResult, testedMethods);
    }

    public void setReporter(TestGeneratorResultReporter reporter) {
        this.reporter = reporter;
    }

    public void setCoverageReporter(CoverageReporter coverageReporter) {
        this.coverageReporter = coverageReporter;
    }

    public void setMethodBuilder(NullCheckingMethodTestBuilder methodBuilder) {
        this.methodBuilder = methodBuilder;
    }

    public void setConstructorBuilder(NullCheckingConstructorTestBuilder constructorBuilder) {
        this.constructorBuilder = constructorBuilder;
    }
}