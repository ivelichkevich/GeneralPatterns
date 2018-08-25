package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.generation.merge.TestNodeMerger;
import com.aurea.testgenerator.generation.source.Imports;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.JavaParser;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class NullCheckingTestBuilderAbstract implements NullCheckingTestBuilder {
    @Autowired
    ValueFactory valueFactory;

    protected String methodName(NullCheckingBuildConfig config) {
        return PREFIX + config.getName() + "_" + config.getException() + postfix();
    }

    protected String postfix() {
        return "_" + RandomStringUtils.random(6, true, true);
    }

    protected DependableNode buidTest(DependableNode node, String test, DependableNode callable) {
        TestNodeMerger.appendDependencies(callable, node);
        callable.setNode(JavaParser.parseBodyDeclaration(test).asMethodDeclaration());
        callable.getDependency().getImports().add(Imports.getJUNIT_TEST());

        return callable;
    }

    @Override
    public void setValueFactory(ValueFactory valueFactory) {
        this.valueFactory = valueFactory;
    }
}
