package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.aurea.testgenerator.generation.patterns.nullchecking.NullCheckingTestTypes.NULL_CHECKING;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.EQUALS;

import com.aurea.testgenerator.ast.Callability;
import com.aurea.testgenerator.generation.TestGenerator;
import com.aurea.testgenerator.generation.TestGeneratorResult;
import com.aurea.testgenerator.reporting.CoverageReporter;
import com.aurea.testgenerator.reporting.TestGeneratorResultReporter;
import com.aurea.testgenerator.source.Unit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * not supported: Stream.of(latitude, longitude).forEach(Objects::requireNonNull);
 * final class as method argument (cant * generate not nul value)
 * changed arg name (eg by = to other reference)
 * invocations of requireNonNull for not args notimplemented by requirements
 * requireNonNull method wrapper
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
                .forEach(m -> buildCallable(classDeclaration, toPublish, result, m, methodBuilder));
    }

    private void generateConstructorTests(ClassOrInterfaceDeclaration classDeclaration,
            List<CallableDeclaration> toPublish, TestGeneratorResult result) {
        classDeclaration.getConstructors().stream().filter(this::constructorMatch)
                .forEach(c -> buildCallable(classDeclaration, toPublish, result, c, constructorBuilder));
    }

    private void buildCallable(ClassOrInterfaceDeclaration classDeclaration, List<CallableDeclaration> toPublish,
            TestGeneratorResult result, CallableDeclaration callableDeclaration, NullCheckingTestBuilder builder) {
        NodeList<Parameter> parameters = callableDeclaration.getParameters();
        findMethodsCall(callableDeclaration, CALL_PATTERTN)
                .forEach(call -> IntStream.range(0, parameters.size()).forEach(i -> {
                    Parameter parameter = parameters.get(i);
                    if (!parameterMatch(parameter)) {
                        return;
                    }
                    if (call.getArguments().get(0).asNameExpr().getNameAsString().equals(parameter.getNameAsString())) {
                        builder.build(classDeclaration, callableDeclaration, parameter, i)
                                .ifPresent(o -> result.getTests().add(o));
                        toPublish.add(callableDeclaration);
                    }
                }));
    }

    private boolean parameterMatch(Parameter parameter) {
        return parameter.getType().isReferenceType();
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
                .filter(c -> !c.isInterface()).collect(Collectors.toList());
    }

    private static List<MethodCallExpr> findMethodsCall(Node node, String methodName) {
        return node.findAll(MethodCallExpr.class, n -> n.getNameAsString().equals(methodName));
    }

    private static List<NameExpr> findIfNullCheckCall(Node node, String methodName) {
        return node.findAll(IfStmt.class).stream()
                .filter(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream()
                        .anyMatch(bi -> (bi.getOperator().equals(EQUALS) && (bi.getRight().isNullLiteralExpr() || bi.getLeft().isNullLiteralExpr())))
                        &&
                        ifStmt.getThenStmt().findAll(ThrowStmt.class).stream()
                                .anyMatch(throwStmt -> throwStmt.findAll(ObjectCreationExpr.class).stream()
                                        .anyMatch(oce -> "IllegalArgumentException".equals(oce.getTypeAsString()))))
                .map(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream()
                        .map(bi -> (bi.getLeft().isNullLiteralExpr() ? bi.getRight() : bi.getLeft()).asNameExpr())
                        .findFirst().get())
                .collect(Collectors.toList());
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