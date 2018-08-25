package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.LiteralExpr;
import java.util.Map;

public class NullCheckingBuildConfig {
    String exception;
    CallableDeclaration callableDeclaration;
    Map<Integer, LiteralExpr> args;


}
