package com.aurea.testgenerator.value.random;

import com.aurea.testgenerator.generation.TestNodeExpression;
import com.aurea.testgenerator.generation.TestNodeVariable;
import com.aurea.testgenerator.value.Types;
import com.aurea.testgenerator.value.ValueFactory;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RandomValueFactory implements ValueFactory {

    public Optional<TestNodeExpression> getExpression(Type type) {
        if (type.isPrimitiveType()) {
            return Optional.of(RandomPrimitiveValueFactory.get(type.asPrimitiveType()));
        } else if (type.isArrayType()) {
            return getExpression(type.asArrayType().getComponentType())
                    .map(testValue -> {
                        Expression node = testValue.getNode();
                        ArrayCreationExpr arrayCreationExpr = new ArrayCreationExpr(type.asArrayType().getElementType());
                        arrayCreationExpr.setInitializer(new ArrayInitializerExpr(NodeList.nodeList(node)));
                        testValue.setNode(arrayCreationExpr);
                        return testValue;
                    });
        } else if (Types.isString(type)) {
            TestNodeExpression testNodeExpression = new TestNodeExpression();
            testNodeExpression.setNode(new StringLiteralExpr(RandomStringPool.next()));
            return Optional.of(testNodeExpression);
        } else if (type.isClassOrInterfaceType()) {
//            return Optional.of(RandomJavaLangTypesFactory.get(type.asClassOrInterfaceType()));
        }
        return Optional.empty();
    }

    public Optional<TestNodeVariable> getVariable(Type type) {

        //new HashMap {{
        // put(1,1);
        //}}

        return Optional.empty();
    }

}
