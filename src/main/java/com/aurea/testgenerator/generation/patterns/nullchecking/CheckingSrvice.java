package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.EQUALS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.GREATER;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.LESS;
import static java.util.stream.Collectors.toList;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CheckingSrvice {
    private static final String CALL_PATTERTN = "requireNonNull";
    private CallableDeclaration callableDeclaration;

    public CheckingSrvice(CallableDeclaration callableDeclaration) {
        this.callableDeclaration = callableDeclaration;

    }

    public List<NullCheckingBuildConfig> createConfigs() {
        List<NullCheckingBuildConfig> configs = new ArrayList<>();

        List<String> args1 = findRequireNonNullArgs(callableDeclaration);
        List<String> args2 = findIfNullCheckArgs(callableDeclaration);

        NodeList<Parameter> parameters = callableDeclaration.getParameters();

        List<String> params = parameters.stream().map(p -> p.getNameAsString()).collect(toList());
        Map<String, List<BinaryExpr>> map = paramsToBinaryExprMap(findRangeCheckCall(callableDeclaration, params), params);


        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            String param = parameter.getNameAsString();
            if (parameterMatch(parameter) && args1.contains(param)) {
                configs.add(new NullCheckingBuildConfig(NullPointerException.class.getSimpleName(), callableDeclaration, ImmutableMap.of(i, new NullLiteralExpr())));
            }
            if (parameterMatch(parameter) && args2.contains(param)) {
                configs.add(new NullCheckingBuildConfig(IllegalArgumentException.class.getSimpleName(), callableDeclaration, ImmutableMap.of(i, new NullLiteralExpr())));
            }
            //Map<Integer, LiteralExpr> args3 = new HashMap<>();
            //map.get(param).forEach();

        }

        return configs;
    }

    private static boolean parameterMatch(Parameter parameter) {
        return parameter.getType().isReferenceType();
    }

    private static List<String> findIfNullCheckArgs(CallableDeclaration callableDeclaration) {
        return findIfNullCheckCall(callableDeclaration).stream().map(m -> m.getNameAsString())
                .collect(Collectors.toList());
    }

    private static List<String> findRequireNonNullArgs(CallableDeclaration callableDeclaration) {
        return findMethodsCall(callableDeclaration, CALL_PATTERTN).stream()
                .map(c -> c.getArguments().get(0).asNameExpr().getNameAsString()).collect(toList());
    }

    private static List<MethodCallExpr> findMethodsCall(Node node, String methodName) {
        return node.findAll(MethodCallExpr.class, n -> n.getNameAsString().equals(methodName));
    }

    private static List<NameExpr> findIfNullCheckCall(Node node) {
        return node.findAll(IfStmt.class).stream()
                .filter(ifStmt -> checkIAE(ifStmt) && checkEqulasNullExp(ifStmt))
                .map(ifStmt -> toNameExp(ifStmt))
                .collect(toList());
    }

    private static List<BinaryExpr> findRangeCheckCall(Node node, List<String> params) {
        //private static Map<NameExpr, LiteralStringValueExpr> findRangeCheckCall(Node node, List<String> params) {
        return node.findAll(IfStmt.class).stream()
                .filter(ifStmt -> checkIAE(ifStmt))
                .flatMap(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream())
                .filter(be -> checkRangeExp(be, params))
                .collect(toList());
    }

    private static boolean checkRangeExp(BinaryExpr bi, List<String> params) {
        if (LESS.equals(bi.getOperator()) || GREATER.equals(bi.getOperator())) {
            return (params.contains(bi.getLeft().toString()) && bi.getRight().isLiteralStringValueExpr())
                    || (bi.getLeft().isLiteralStringValueExpr() && params.contains(bi.getRight().toString()));
        }
        return false;
    }

    private static Map<String, List<BinaryExpr>> paramsToBinaryExprMap(List<BinaryExpr> binaryExprs, List<String> params) {
        Map<String, List<BinaryExpr>> paramsToBinaryExprMap = new HashMap<>();
        for (String p : params) {
            for (BinaryExpr be : binaryExprs) {
                if (checkBinaryExprForParam(be, p)) {
                    if (!paramsToBinaryExprMap.containsKey(p)) {
                        paramsToBinaryExprMap.put(p, new ArrayList<>());
                    }
                    paramsToBinaryExprMap.get(p).add(be);
                }
            }
        }
        return paramsToBinaryExprMap;
    }

    private static boolean checkBinaryExprForParam(BinaryExpr bi, String param) {
        return (param.equals(bi.getLeft().toString()) && bi.getRight().isLiteralStringValueExpr())
                || (bi.getLeft().isLiteralStringValueExpr() && param.equals(bi.getRight().toString()));
    }

    private static boolean checkIAE(IfStmt ifStmt) {
        return ifStmt.getThenStmt().findAll(ThrowStmt.class).stream()
                .anyMatch(throwStmt -> throwStmt.findAll(ObjectCreationExpr.class).stream()
                        .anyMatch(oce -> "IllegalArgumentException".equals(oce.getTypeAsString())));
    }

    private static boolean checkEqulasNullExp(IfStmt ifStmt) {
        return ifStmt.findAll(BinaryExpr.class).stream()
                .anyMatch(bi -> (bi.getOperator().equals(EQUALS) && (bi.getRight().isNullLiteralExpr() || bi
                        .getLeft().isNullLiteralExpr())));
    }

    private static NameExpr toNameExp (IfStmt ifStmt) {
        return ifStmt.findAll(BinaryExpr.class).stream()
                .map(bi -> (bi.getLeft().isNullLiteralExpr() ? bi.getRight() : bi.getLeft()).asNameExpr())
                .findFirst().get();
    }
}
