package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.ast.InvocationBuilder;
import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.generation.merge.TestNodeMerger;
import com.aurea.testgenerator.generation.source.Imports;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.Optional;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NullCheckingConstructorTestBuilder extends NullCheckingTestBuilderAbstract {
    @Override
    public Optional<DependableNode> build(NullCheckingBuildConfig config) {
        InvocationBuilder invocationBuilder = new InvocationBuilder(valueFactory);
        Optional<DependableNode<ObjectCreationExpr>> maybeConstructor = invocationBuilder
                .build((ConstructorDeclaration) config.getCallable());
        if (!maybeConstructor.isPresent()) {
            return Optional.empty();
        }

        DependableNode<ObjectCreationExpr> constructor = maybeConstructor.get();
        config.args.forEach((key, value) -> constructor.getNode().getArguments().set(key, value));

        String test = "@Test(expected = " + config.getException() + ".class)\n"
                + "    public void " + methodName(config) + "(){\n"
                + "        " + constructor.toString() + ";\n"
                + "    }";

        DependableNode<MethodDeclaration> testMethod = new DependableNode<>();

        return Optional.of(buidTest(constructor, test, testMethod));
    }
}
