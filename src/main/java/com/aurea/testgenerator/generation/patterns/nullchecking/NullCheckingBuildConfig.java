package com.aurea.testgenerator.generation.patterns.nullchecking;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.LiteralExpr;
import java.util.Map;

public class NullCheckingBuildConfig {
    String exception;
    CallableDeclaration callable;
    Map<Integer, LiteralExpr> args;

    public NullCheckingBuildConfig(String exception, CallableDeclaration callable,
            Map<Integer, LiteralExpr> args) {
        this.exception = exception;
        this.callable = callable;
        this.args = args;
    }

    public String getException() {
        return exception;
    }

    public CallableDeclaration getCallable() {
        return callable;
    }

    public Map<Integer, LiteralExpr> getArgs() {
        return args;
    }

    public String getName() {
        return callable.getNameAsString();
    }
}
