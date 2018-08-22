package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.aurea.testgenerator.generation.patterns.nullchecking.NullCheckingTestTypes.NULL_CHECKING;

import com.aurea.testgenerator.ast.Callability;
import com.aurea.testgenerator.generation.TestGenerator;
import com.aurea.testgenerator.generation.TestGeneratorResult;
import com.aurea.testgenerator.generation.names.NomenclatureFactory;
import com.aurea.testgenerator.generation.names.TestMethodNomenclature;
import com.aurea.testgenerator.reporting.CoverageReporter;
import com.aurea.testgenerator.reporting.TestGeneratorResultReporter;
import com.aurea.testgenerator.source.Unit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.BlockStmt;
import groovy.util.logging.Log4j2;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("null-checking")
@Log4j2
public class NullCheckingTestGenerator implements TestGenerator {

    //Objects.requireNonNull(
    //Stream.of(latitude, longitude).forEach(Objects::requireNonNull);
    //requireNonNull(

    private static final String PATTERTN_1 = "Objects.requireNonNull(";
    private static final String PATTERTN_11 = "Objects.requireNonNull({1})";
    private static final String PATTERTN_2 = "requireNonNull";
    private static final String PATTERTN_4 = "java.util.Objects";

    @Autowired
    private TestGeneratorResultReporter reporter;

    @Autowired
    private CoverageReporter coverageReporter;

    @Autowired
    private NomenclatureFactory nomenclatures;

    @Override
    public Collection<TestGeneratorResult> generate(Unit unit) {
        if (unit.getCu().getImports().stream().map(ImportDeclaration::getName).noneMatch(n -> n.asString()
                .startsWith(PATTERTN_4))) { //* is not supported
            return Collections.EMPTY_LIST;
        }
        List<ClassOrInterfaceDeclaration> classes = unit.getCu().findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface()).collect(Collectors.toList());
        TestMethodNomenclature testMethodNomenclature = nomenclatures.getTestMethodNomenclature(unit.getJavaClass());
        List<TestGeneratorResult> tests = new ArrayList<>();

        for (ClassOrInterfaceDeclaration classDeclaration : classes) {
            TestGeneratorResult result = new TestGeneratorResult();
            result.setType(NULL_CHECKING);

            if (Callability.isInstantiable(classDeclaration) && classDeclaration.toString().contains(PATTERTN_2)) {
                for(MethodDeclaration methodDeclaration : classDeclaration.getMethods()) {
                    if (!methodDeclaration.getParameters().isEmpty() && methodDeclaration.getBody().isPresent()
                            && methodDeclaration.getBody().get().toString().contains(PATTERTN_2)) { //methods and constructors with params support only
                        Map<String, Parameter> map = methodDeclaration.getParameters().stream().collect(Collectors
                                .toMap(p -> PATTERTN_2 + "\\s*(\\s*" + p.getNameAsString() + "\\s*)", p -> p));
                        methodDeclaration.getBody().get().getStatements().get(0);

                    }
                }
            }
        }
        return tests;
    }
}