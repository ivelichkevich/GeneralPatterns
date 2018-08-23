package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.util.Optional;

public interface NullCheckingTestBuilder {

    String PASS_NULL_TO = "_passNullAs_";
    String PREFIX = "test_";
    String RESULT = "_NPE";

    Optional<DependableNode<MethodDeclaration>> build(ClassOrInterfaceDeclaration classDeclaration,
            CallableDeclaration callableDeclaration, Parameter parameter, int order);

    void setValueFactory(ValueFactory valueFactory);
}
