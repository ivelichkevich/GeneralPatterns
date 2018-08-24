package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.aurea.testgenerator.generation.patterns.nullchecking.NullCheckingTestTypes.NULL_CHECKING;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.EQUALS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.LESS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.GREATER;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;

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
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
                .forEach(m -> buildCallable(classDeclaration, toPublish, result, m, methodBuilder));
    }

    private void generateConstructorTests(ClassOrInterfaceDeclaration classDeclaration,
            List<CallableDeclaration> toPublish, TestGeneratorResult result) {
        classDeclaration.getConstructors().stream().filter(this::constructorMatch)
                .forEach(c -> buildCallable(classDeclaration, toPublish, result, c, constructorBuilder));
    }

    private void buildCallable(ClassOrInterfaceDeclaration classDeclaration, List<CallableDeclaration> toPublish,
            TestGeneratorResult result, CallableDeclaration callableDeclaration, NullCheckingTestBuilder builder) {
        List<String> args = findRequireNonNullArgs(callableDeclaration);
        buildCallableWithArgs(classDeclaration, toPublish, result, callableDeclaration, builder, args,
                NullPointerException.class.getSimpleName());
        args = findIfNullCheckArgs(callableDeclaration);
        buildCallableWithArgs(classDeclaration, toPublish, result, callableDeclaration, builder, args,
                IllegalArgumentException.class.getSimpleName());
    }

    private List<String> findIfNullCheckArgs(CallableDeclaration callableDeclaration) {
        return findIfNullCheckCall(callableDeclaration).stream().map(m -> m.getNameAsString())
                .collect(Collectors.toList());
    }

    private List<String> findRequireNonNullArgs(CallableDeclaration callableDeclaration) {
        return findMethodsCall(callableDeclaration, CALL_PATTERTN).stream()
                .map(c -> c.getArguments().get(0).asNameExpr().getNameAsString()).collect(toList());
    }

    private void buildCallableWithArgs(ClassOrInterfaceDeclaration classDeclaration,
            List<CallableDeclaration> toPublish, TestGeneratorResult result, CallableDeclaration callableDeclaration,
            NullCheckingTestBuilder builder, List<String> args, String exceptionName) {
        NodeList<Parameter> parameters = callableDeclaration.getParameters();
        Map<String, Integer> params = range(0, parameters.size()).boxed()
                .collect(toMap(parameters::get, i -> i))
                .entrySet().stream().filter(e -> parameterMatch(e.getKey()))
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey().getNameAsString(), e.getValue()))
                .filter(e -> args.contains(e.getKey()))
                .collect(toMap(e -> e.getKey(), e -> e.getValue()));

        params.forEach((k, v) -> {
            builder.build(classDeclaration, callableDeclaration, k, v, exceptionName)
                    .ifPresent(o -> result.getTests().add(o));
            toPublish.add(callableDeclaration);
        });
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
                .filter(c -> !c.isInterface()).collect(toList());
    }

    private static List<MethodCallExpr> findMethodsCall(Node node, String methodName) {
        return node.findAll(MethodCallExpr.class, n -> n.getNameAsString().equals(methodName));
    }

    private static List<NameExpr> findIfNullCheckCall(Node node) {
        return node.findAll(IfStmt.class).stream()
                .filter(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream()
                        .anyMatch(bi -> (bi.getOperator().equals(EQUALS) && (bi.getRight().isNullLiteralExpr() || bi
                                .getLeft().isNullLiteralExpr())))
                        &&
                        ifStmt.getThenStmt().findAll(ThrowStmt.class).stream()
                                .anyMatch(throwStmt -> throwStmt.findAll(ObjectCreationExpr.class).stream()
                                        .anyMatch(oce -> "IllegalArgumentException".equals(oce.getTypeAsString()))))
                .map(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream()
                        .map(bi -> (bi.getLeft().isNullLiteralExpr() ? bi.getRight() : bi.getLeft()).asNameExpr())
                        .findFirst().get())
                .collect(toList());
    }

//    private static List<NameExpr> findRangeCheckCall(Node node, List<String> params) {
//        return node.findAll(IfStmt.class).stream()
//                .filter(ifStmt ->
//                        ifStmt.getThenStmt().findAll(ThrowStmt.class).stream()
//                                .anyMatch(throwStmt -> throwStmt.findAll(ObjectCreationExpr.class).stream()
//                                        .anyMatch(oce -> "IllegalArgumentException".equals(oce.getTypeAsString())))
//                        &&
//                        ifStmt.findAll(BinaryExpr.class).stream()
//                                .anyMatch(bi -> (bi.getOperator().equals(LESS) && (bi.getRight().isNullLiteralExpr() || bi
//                                        .getLeft().isNullLiteralExpr()))))
//                .map(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream()
//                        .map(bi -> (bi.getLeft().isNullLiteralExpr() ? bi.getRight() : bi.getLeft()).asNameExpr())
//                        .findFirst().get())
//                .collect(toList());
//
////        node.findAll(IfStmt.class).stream().findAny().get().findAll(BinaryExpr.class).stream()
////                .filter(bi -> (bi.getOperator().asString().equals(LESS) || bi.getOperator().asString().equals(GREATER))
////                        && (params.contains(bi.getLeft().toString()) || params.contains(bi.getRight().toString())))
////                .collect(Collectors.toList())
//    }

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