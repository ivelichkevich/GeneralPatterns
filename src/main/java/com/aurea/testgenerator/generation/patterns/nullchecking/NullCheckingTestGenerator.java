package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.aurea.testgenerator.generation.patterns.nullchecking.NullCheckingTestTypes.NULL_CHECKING;

import com.aurea.testgenerator.ast.ASTNodeUtils;
import com.aurea.testgenerator.ast.Callability;
import com.aurea.testgenerator.ast.InvocationBuilder;
import com.aurea.testgenerator.generation.TestGenerator;
import com.aurea.testgenerator.generation.TestGeneratorResult;
import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.generation.merge.TestNodeMerger;
import com.aurea.testgenerator.generation.names.NomenclatureFactory;
import com.aurea.testgenerator.generation.names.TestMethodNomenclature;
import com.aurea.testgenerator.generation.source.Imports;
import com.aurea.testgenerator.reporting.CoverageReporter;
import com.aurea.testgenerator.reporting.TestGeneratorResultReporter;
import com.aurea.testgenerator.source.Unit;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * not supported:
 * Stream.of(latitude, longitude).forEach(Objects::requireNonNull);
 * final class as method argument (cant generate not nul value)
 * changed arg name (eg by = to other reference)
 * invocations of requireNonNull for not args not implemented by requirements
 * requireNonNull method wrapper
 *
 * primitive types ignored
 */
@Component
@Profile("null-checking")
public class NullCheckingTestGenerator implements TestGenerator {

    private static Logger logger = LogManager.getLogger(NullCheckingTestGenerator.class);

    private static final String PASS_NULL_TO = "_passNullAs_";
    private static final String PREFIX = "test_";
    private static final String RESULT = "_NPE";
    private static final String CALL_PATTERTN = "requireNonNull";
    private static final String IMPORT_PATTERTN = "java.util.";

    @Autowired
    private TestGeneratorResultReporter reporter;

    @Autowired
    private CoverageReporter coverageReporter;

    @Autowired
    private NomenclatureFactory nomenclatures;

    @Autowired
    ValueFactory valueFactory;

    @Override
    public Collection<TestGeneratorResult> generate(Unit unit) {
        if (unit.getCu().getImports().stream().map(ImportDeclaration::getName).noneMatch(n -> n.asString()
                .startsWith(IMPORT_PATTERTN))) {
            return Collections.EMPTY_LIST;
        }
        List<ClassOrInterfaceDeclaration> classes = unit.getCu().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface()).collect(Collectors.toList());
        TestMethodNomenclature testMethodNomenclature = nomenclatures.getTestMethodNomenclature(unit.getJavaClass());
        logger.info(testMethodNomenclature);
        List<TestGeneratorResult> tests = new ArrayList<>();

        for (ClassOrInterfaceDeclaration classDeclaration : classes) {
            if (!Callability.isInstantiable(classDeclaration) || !classDeclaration.toString().contains(CALL_PATTERTN)) {
                continue;
            }

            TestGeneratorResult result = new TestGeneratorResult();
            result.setType(NULL_CHECKING);

            List<CallableDeclaration> toPublish = new ArrayList<>();
            for (MethodDeclaration methodDeclaration : classDeclaration.getMethods()) {
                if (methodDeclaration.getParameters().isEmpty() || !methodDeclaration.getBody().isPresent()
                        || methodDeclaration.getBody().get().isEmpty()) {
                    continue;
                }
                NodeList<Parameter> parameters = methodDeclaration.getParameters();
                for (MethodCallExpr call : findMethodsCall(methodDeclaration, CALL_PATTERTN)) {
                    for (int i = 0; i < parameters.size(); i++) {
                        Parameter parameter = parameters.get(i);
                        if (!parameter.getType().isReferenceType()) {
                            continue;
                        }
                        if (call.getArguments().get(0).asNameExpr().getNameAsString()
                                .equals(parameter.getNameAsString())) {
                            buildMethodTest(classDeclaration, methodDeclaration, parameter, i)
                                    .ifPresent(o -> result.getTests().add(o));
                            toPublish.add(methodDeclaration);
                        }
                    }
                }
            }

            for (ConstructorDeclaration constructorDeclaration : classDeclaration.getConstructors()) {
                if (constructorDeclaration.getParameters().isEmpty() || constructorDeclaration.getBody().isEmpty()) {
                    continue;
                }
                NodeList<Parameter> parameters = constructorDeclaration.getParameters();
                for (MethodCallExpr call : findMethodsCall(constructorDeclaration, CALL_PATTERTN)) {
                    for (int i = 0; i < parameters.size(); i++) {
                        Parameter parameter = parameters.get(i);
                        if (!parameter.getType().isReferenceType()) {
                            continue;
                        }
                        if (call.getArguments().get(0).asNameExpr().getNameAsString()
                                .equals(parameter.getNameAsString())) {
                            buildConstructorTest(constructorDeclaration, parameter, i)
                                    .ifPresent(o -> result.getTests().add(o));
                            toPublish.add(constructorDeclaration);
                        }
                    }
                }
            }

            if (!result.getTests().isEmpty()) {
                tests.add(result);
                publishAndAdd(result, unit, toPublish);
            }
        }
        return tests;
    }

    private Optional<DependableNode<MethodDeclaration>> buildConstructorTest(
            ConstructorDeclaration methodDeclaration, Parameter p, int order) {
        InvocationBuilder invocationBuilder = new InvocationBuilder(valueFactory);
        Optional<DependableNode<ObjectCreationExpr>> maybeConstructor = invocationBuilder.build(methodDeclaration);
        if (!maybeConstructor.isPresent()) {
            return Optional.empty();
        }
        DependableNode<MethodDeclaration> testMethod = new DependableNode<>();

        DependableNode<ObjectCreationExpr> constructor = maybeConstructor.get();
        constructor.getNode().getArguments().set(order, new NullLiteralExpr());
        TestNodeMerger.appendDependencies(testMethod, constructor);

        String methodName =
                PREFIX + constructor.getNode().getTypeAsString() + PASS_NULL_TO + p.getNameAsString() + RESULT;

        String test = "@Test(expected = NullPointerException.class)\n"
                + "    public void " + methodName + "(){\n"
                + "        " + constructor.toString() + ";\n"
                + "    }";

        testMethod.setNode(JavaParser.parseBodyDeclaration(test).asMethodDeclaration());
        testMethod.getDependency().getImports().add(Imports.getJUNIT_TEST());

        return Optional.of(testMethod);
    }

    private Optional<DependableNode<MethodDeclaration>> buildMethodTest(ClassOrInterfaceDeclaration classDeclaration,
            MethodDeclaration methodDeclaration, Parameter parameter, int order) {
        String fullTypeName = ASTNodeUtils.getFullTypeName(classDeclaration);
        InvocationBuilder invocationBuilder = new InvocationBuilder(valueFactory);
        DependableNode<MethodDeclaration> testMethod = new DependableNode<>();
        String newObject = "new " + fullTypeName + "()";
        if (!classDeclaration.getConstructors().isEmpty()) {
            Optional<DependableNode<ObjectCreationExpr>> maybeConstructor = invocationBuilder
                    .build(classDeclaration.getConstructors().get(0));
            if (maybeConstructor.isPresent()) {
                DependableNode<ObjectCreationExpr> constructor = maybeConstructor.get();
                TestNodeMerger.appendDependencies(testMethod, constructor);
                newObject = constructor.toString();
            }
        }

        Optional<DependableNode<MethodCallExpr>> maybeMethod = invocationBuilder
                .buildMethodInvocation(methodDeclaration);
        if (!maybeMethod.isPresent()) {
            return Optional.empty();
        }
        DependableNode<MethodCallExpr> method = maybeMethod.get();
        method.getNode().getArguments().set(order, new NullLiteralExpr());
        TestNodeMerger.appendDependencies(testMethod, method);

        String methodName =
                PREFIX + method.getNode().getName().asString() + PASS_NULL_TO + parameter.getNameAsString() + RESULT;

        String test = "@Test(expected = NullPointerException.class)\n"
                + "    public void " + methodName + "(){\n"
                + "        " + fullTypeName + " o = " + newObject + ";\n"
                + "        o." + method.toString() + ";\n"
                + "    }";

        testMethod.setNode(JavaParser.parseBodyDeclaration(test).asMethodDeclaration());
        testMethod.getDependency().getImports().add(Imports.getJUNIT_TEST());

        return Optional.of(testMethod);
    }

    private static List<MethodCallExpr> findMethodsCall(Node node, String methodName) {
        return node.findAll(MethodCallExpr.class, n -> n.getNameAsString().equals(methodName));
    }

    private void publishAndAdd(TestGeneratorResult testGeneratorResult, Unit unit,
            List<CallableDeclaration> testedMethods) {
        reporter.publish(testGeneratorResult, unit, testedMethods);
        coverageReporter.report(unit, testGeneratorResult, testedMethods);
    }

    public void setNomenclatures(NomenclatureFactory nomenclatures) {
        this.nomenclatures = nomenclatures;
    }

    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }

    public void setReporter(TestGeneratorResultReporter reporter) {
        this.reporter = reporter;
    }

    public void setCoverageReporter(CoverageReporter coverageReporter) {
        this.coverageReporter = coverageReporter;
    }
}