package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.ast.InvocationBuilder;
import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.generation.merge.TestNodeMerger;
import com.aurea.testgenerator.generation.source.Imports;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NullCheckingConstructorTestBuilder implements NullCheckingTestBuilder {
    @Autowired
    ValueFactory valueFactory;

    @Override
    public Optional<DependableNode<MethodDeclaration>> build(ClassOrInterfaceDeclaration classDeclaration,
            CallableDeclaration callableDeclaration, Parameter parameter, int order) {
        InvocationBuilder invocationBuilder = new InvocationBuilder(valueFactory);
        Optional<DependableNode<ObjectCreationExpr>> maybeConstructor = invocationBuilder.build((ConstructorDeclaration)callableDeclaration);
        if (!maybeConstructor.isPresent()) {
            return Optional.empty();
        }
        DependableNode<MethodDeclaration> testMethod = new DependableNode<>();

        DependableNode<ObjectCreationExpr> constructor = maybeConstructor.get();
        constructor.getNode().getArguments().set(order, new NullLiteralExpr());
        TestNodeMerger.appendDependencies(testMethod, constructor);

        String methodName = PREFIX + RandomStringUtils.random(6, true, true)
                + "_" +  constructor.getNode().getTypeAsString() + PASS_NULL_TO + parameter.getNameAsString() + RESULT;

        String test = "@Test(expected = NullPointerException.class)\n"
                + "    public void " + methodName + "(){\n"
                + "        " + constructor.toString() + ";\n"
                + "    }";

        testMethod.setNode(JavaParser.parseBodyDeclaration(test).asMethodDeclaration());
        testMethod.getDependency().getImports().add(Imports.getJUNIT_TEST());

        return Optional.of(testMethod);
    }

    @Override
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }
}
