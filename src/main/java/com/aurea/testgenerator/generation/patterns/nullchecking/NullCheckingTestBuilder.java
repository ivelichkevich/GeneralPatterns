package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.aurea.testgenerator.generation.ast.DependableNode;
import com.aurea.testgenerator.value.ValueFactory;
import java.util.Optional;

public interface NullCheckingTestBuilder {

    String PASS_NULL_TO = "_passNullAs_";
    String PREFIX = "test_";
    String RESULT = "_NPE";

    Optional<DependableNode> build(NullCheckingBuildConfig config);

    void setValueFactory(ValueFactory valueFactory);
}
