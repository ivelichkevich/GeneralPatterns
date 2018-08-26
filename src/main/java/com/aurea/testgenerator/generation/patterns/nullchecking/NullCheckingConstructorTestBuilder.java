package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.ast.InvocationBuilder;
import com.aurea.testgenerator.generation.ast.DependableNode;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.Optional;
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
