package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.ast.ASTNodeUtils;
import com.aurea.testgenerator.ast.InvocationBuilder;
import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.generation.merge.TestNodeMerger;
import com.aurea.testgenerator.generation.source.Imports;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NullCheckingMethodTestBuilder extends NullCheckingTestBuilderAbstract {

    @Override
    public Optional<DependableNode> build(NullCheckingBuildConfig config) {
        ClassOrInterfaceDeclaration classDeclaration = (ClassOrInterfaceDeclaration) config.getCallable()
                .getParentNode().get();
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
                .buildMethodInvocation(config.getCallable());
        if (!maybeMethod.isPresent()) {
            return Optional.empty();
        }
        DependableNode<MethodCallExpr> method = maybeMethod.get();
        config.args.forEach((key, value) -> method.getNode().getArguments().set(key, value));

        String test = "@Test(expected = " + config.getException() + ".class)\n"
                + "    public void " + methodName(config) + "(){\n"
                + "        " + fullTypeName + " o = " + newObject + ";\n"
                + "        o." + method.toString() + ";\n"
                + "    }";
        return Optional.of(buidTest(method, test, testMethod));
    }
}
