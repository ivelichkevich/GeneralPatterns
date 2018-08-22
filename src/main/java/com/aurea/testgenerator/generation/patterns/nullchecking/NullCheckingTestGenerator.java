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
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("null-checking")
public class NullCheckingTestGenerator implements TestGenerator {
    private static Logger logger = LogManager.getLogger(NullCheckingTestGenerator.class);
    // Objects.requireNonNull(
    // requireNonNull(
    // Stream.of(latitude, longitude).forEach(Objects::requireNonNull); //not supported
    // final class as method argument not supported
    // changed arg name (eg by =) is not supported
    // invocations of requireNonNull for not args not implemented by requirements
    // primitive types ignored
    // method wrapper is not supported

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
                .startsWith(IMPORT_PATTERTN))) { // ToDo * is not supported
            return Collections.EMPTY_LIST;
        }
        List<ClassOrInterfaceDeclaration> classes = unit.getCu().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface()).collect(Collectors.toList());
        TestMethodNomenclature testMethodNomenclature = nomenclatures.getTestMethodNomenclature(unit.getJavaClass());
        List<TestGeneratorResult> tests = new ArrayList<>();

        for (ClassOrInterfaceDeclaration classDeclaration : classes) {
            if (!Callability.isInstantiable(classDeclaration) || !classDeclaration.toString().contains(CALL_PATTERTN) ) {
                continue;
            }

            TestGeneratorResult result = new TestGeneratorResult();
            result.setType(NULL_CHECKING);

            for(MethodDeclaration methodDeclaration : classDeclaration.getMethods()) {
                if (methodDeclaration.getParameters().isEmpty() || !methodDeclaration.getBody().isPresent()
                        || methodDeclaration.getBody().get().isEmpty()
                        //|| !methodDeclaration.getBody().get().toString().contains(CALL_PATTERTN)
                        ) {
                    continue;
                }
                NodeList<Parameter> parameters = methodDeclaration.getParameters();
                for (MethodCallExpr call : findMethodsCall(methodDeclaration, CALL_PATTERTN)) {
                    for (int i = 0; i < parameters.size(); i++) {
                        Parameter parameter = parameters.get(i);
                        if (!parameter.getType().isReferenceType()) {
                            continue;
                        }
                        if (call.getArguments().get(0).asNameExpr().getNameAsString().equals(parameter.getNameAsString())) {
                            buildMethodTest(classDeclaration, methodDeclaration, parameter, i)
                                    .ifPresent(o -> result.getTests().add(o));
                        }
                    }
                }
            }

            for(ConstructorDeclaration constructorDeclaration : classDeclaration.getConstructors()) {
                if (constructorDeclaration.getParameters().isEmpty() || constructorDeclaration.getBody().isEmpty()
                    //|| !constructorDeclaration.getBody().get().toString().contains(CALL_PATTERTN)
                        ) {
                    continue;
                }
                NodeList<Parameter> parameters = constructorDeclaration.getParameters();
                for (MethodCallExpr call : findMethodsCall(constructorDeclaration, CALL_PATTERTN)) {
                    for (int i = 0; i < parameters.size(); i++) {
                        Parameter parameter = parameters.get(i);
                        if (!parameter.getType().isReferenceType()) {
                            continue;
                        }
                        if (call.getArguments().get(0).asNameExpr().getNameAsString().equals(parameter.getNameAsString())) {
                            buildConstructorTest(classDeclaration, constructorDeclaration, parameter, i)
                                    .ifPresent(o -> result.getTests().add(o));
                        }
                    }
                }
            }

            if (!result.getTests().isEmpty()) {
                tests.add(result);
            }
        }
        return tests;
    }

    private Optional<DependableNode<MethodDeclaration>> buildConstructorTest(ClassOrInterfaceDeclaration classDeclaration,
            ConstructorDeclaration methodDeclaration, Parameter p, int order) {
        String fullTypeName = ASTNodeUtils.getFullTypeName(classDeclaration);
        InvocationBuilder invocationBuilder = new InvocationBuilder(valueFactory);
        Optional<DependableNode<ObjectCreationExpr>> maybeConstructor = invocationBuilder.build(methodDeclaration);
        if (!maybeConstructor.isPresent()) {
            return Optional.empty();
        }
        DependableNode<MethodDeclaration> testMethod = new DependableNode<>();

        DependableNode<ObjectCreationExpr> constructor = maybeConstructor.get();
        constructor.getNode().getArguments().set(order, new NullLiteralExpr());
        TestNodeMerger.appendDependencies(testMethod, constructor);

        String methodName = "test_" + constructor.getNode().getTypeAsString() + "_passNullAs" + p.getNameAsString() + "_NPE";

        String test = "@Test(expected = NullPointerException.class)\n"
                + "    public void " + methodName + "(){\n"
                + "        " + constructor.toString() + ";\n"
                + "    }";

        testMethod.setNode(JavaParser.parseBodyDeclaration(test).asMethodDeclaration());
        testMethod.getDependency().getImports().add(Imports.getJUNIT_TEST());

        logger.info("IHVE : \n" + testMethod);

        return Optional.of(testMethod);
    }

    private Optional<DependableNode<MethodDeclaration>> buildMethodTest(
            ClassOrInterfaceDeclaration classDeclaration,
            MethodDeclaration methodDeclaration,
            Parameter parameter,
            int order) {
        String fullTypeName = ASTNodeUtils.getFullTypeName(classDeclaration);
        InvocationBuilder invocationBuilder = new InvocationBuilder(valueFactory);
        //Map<String, DependableNode<Expression>> expressionsForParameters = new HashMap<>();
        //expressionsForParameters.put(parameter.getNameAsString(), DependableNode.from(new NullLiteralExpr()));
        Optional<DependableNode<ObjectCreationExpr>> maybeConstructor = invocationBuilder.build(classDeclaration.getConstructors().get(0));
        Optional<DependableNode<MethodCallExpr>> maybeMethod = invocationBuilder.buildMethodInvocation(methodDeclaration);
        if (!maybeMethod.isPresent() || !maybeConstructor.isPresent()) {
            return Optional.empty();
        }
        DependableNode<MethodDeclaration> testMethod = new DependableNode<>();
        DependableNode<MethodCallExpr> method = maybeMethod.get();
        method.getNode().getArguments().set(order, new NullLiteralExpr());
        TestNodeMerger.appendDependencies(testMethod, method);
        DependableNode<ObjectCreationExpr> constructor = maybeConstructor.get();
        TestNodeMerger.appendDependencies(testMethod, constructor);

        String methodName = "test_" + method.getNode().getName().asString() + "_passNullAs" + parameter.getNameAsString() + "_NPE";

        String test = "@Test(expected = NullPointerException.class)\n"
                + "    public void " + methodName + "(){\n"
                + "        " + fullTypeName + " o = " + constructor.toString() + ";\n"
                + "        o." + method.toString() + ";\n"
                + "    }";

        testMethod.setNode(JavaParser.parseBodyDeclaration(test).asMethodDeclaration());
        testMethod.getDependency().getImports().add(Imports.getJUNIT_TEST());

        logger.info("IHVE : \n" + testMethod);

        return Optional.of(testMethod);
    }

    private static List<MethodCallExpr> findMethodsCall(Node node, String methodName) {
        return node.findAll(MethodCallExpr.class, n -> n.getNameAsString().equals(methodName));
    }
}