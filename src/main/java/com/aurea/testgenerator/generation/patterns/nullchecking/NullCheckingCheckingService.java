package com.aurea.testgenerator.generation.patterns.nullchecking;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.EQUALS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.GREATER;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.GREATER_EQUALS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.LESS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.LESS_EQUALS;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.OR;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.AND;
import static java.util.stream.Collectors.toList;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NullCheckingCheckingService {

    private static final String CALL_PATTERN = "requireNonNull";
    private CallableDeclaration callableDeclaration;

    public NullCheckingCheckingService(CallableDeclaration callableDeclaration) {
        this.callableDeclaration = callableDeclaration;

    }

    public List<NullCheckingBuildConfig> createConfigs() {
        List<NullCheckingBuildConfig> configs = new ArrayList<>();

        List<String> args1 = findRequireNonNullArgs(callableDeclaration);
        List<String> args2 = findIfNullCheckArgs(callableDeclaration);

        NodeList<Parameter> parameters = callableDeclaration.getParameters();

        List<String> params = parameters.stream().map(p -> p.getNameAsString()).collect(toList());
        Map<String, List<BinaryExpr>> map = paramsToBinaryExprMap(findRangeCheckCall(callableDeclaration, params),
                params);

        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            String param = parameter.getNameAsString();
            if (parameterMatch(parameter) && args1.contains(param)) {
                configs.add(new NullCheckingBuildConfig(NullPointerException.class.getSimpleName(), callableDeclaration,
                        ImmutableMap.of(i, new NullLiteralExpr())));
            }
            if (parameterMatch(parameter) && args2.contains(param)) {
                configs.add(
                        new NullCheckingBuildConfig(IllegalArgumentException.class.getSimpleName(), callableDeclaration,
                                ImmutableMap.of(i, new NullLiteralExpr())));
            }
            if (map.containsKey(param)) {
                List<BinaryExpr> list = map.get(param);
                if (!list.isEmpty() && list.size() <= 2) {
                    Node parent = list.get(0).getParentNode().get();
                    if (parent instanceof BinaryExpr && parent.getParentNode().isPresent() && parent.getParentNode()
                            .get() instanceof IfStmt
                            && list.contains(((BinaryExpr) parent).getLeft()) && list
                            .contains(((BinaryExpr) parent).getLeft())) {
                        if (((BinaryExpr) parent).getOperator().equals(OR)) {
                            configs.add(new NullCheckingBuildConfig(IllegalArgumentException.class.getSimpleName(),
                                    callableDeclaration,
                                    ImmutableMap.of(i, getOnlyFirstTrue(list.get(0), list.get(1), OR))));
                            configs.add(new NullCheckingBuildConfig(IllegalArgumentException.class.getSimpleName(),
                                    callableDeclaration,
                                    ImmutableMap.of(i, getOnlyFirstTrue(list.get(1), list.get(0), OR))));
                        } else if (((BinaryExpr) parent).getOperator().equals(AND)) {
                            configs.add(new NullCheckingBuildConfig(IllegalArgumentException.class.getSimpleName(),
                                    callableDeclaration,
                                    ImmutableMap.of(i, getOnlyFirstTrue(list.get(0), list.get(1), AND))));
                        }
                    } else if (parent instanceof IfStmt) {
                        configs.add(new NullCheckingBuildConfig(IllegalArgumentException.class.getSimpleName(),
                                callableDeclaration,
                                ImmutableMap.of(i, getOnlyFirstTrue(list.get(0), list.get(0), OR))));
                    }
                }

            }

        }

        return configs;
    }

    private LiteralExpr getOnlyFirstTrue(BinaryExpr binaryExpr1, BinaryExpr binaryExpr2, Operator operator) {
        LiteralExpr result = null;

        ExpressionOperatorSign expressionOperatorSign1 = new ExpressionOperatorSign(binaryExpr1).invoke();
        Expression exp1 = expressionOperatorSign1.getExpression();
        int sign1 = expressionOperatorSign1.getSign();
        Operator op1 = expressionOperatorSign1.getOperator();

        ExpressionOperatorSign expressionOperatorSign2 = new ExpressionOperatorSign(binaryExpr2).invoke();
        Expression exp2 = expressionOperatorSign2.getExpression();
        int sign2 = expressionOperatorSign2.getSign();
        Operator op2 = expressionOperatorSign2.getOperator();

        if (operator.equals(AND)) {
            if (exp1.isIntegerLiteralExpr()) {
                result = new IntegerLiteralExpr(getIntegerValue(exp1, op1, sign1));
            } else if (exp1.isLongLiteralExpr()) {
                result = new LongLiteralExpr(getaLongValue(exp1, op1, sign1));
            } else if (exp1.isDoubleLiteralExpr()) {
                result = new DoubleLiteralExpr(getaDoubleForAnd(exp1, sign1, exp2, sign2));
            }

        } else if (operator.equals(OR)) {
            if (exp1.isIntegerLiteralExpr()) {
                result = new IntegerLiteralExpr(getIntegerValue(exp1, op1, sign1));
            } else if (exp1.isLongLiteralExpr()) {
                result = new LongLiteralExpr(getaLongValue(exp1, op1, sign1));
            } else if (exp1.isDoubleLiteralExpr()) {
                result = new DoubleLiteralExpr(getaDoubleForOr(exp1, op1, sign1));
            }
        }
        return result;
    }

    private Double getaDoubleForOr(Expression exp1, Operator op1, int sign1) {
        Double value = Double.parseDouble(exp1.asDoubleLiteralExpr().getValue());
        value *= sign1;
        if (op1.equals(LESS)) {
            value--;
        } else if (op1.equals(GREATER)) {
            value++;
        }
        return value;
    }

    private Double getaDoubleForAnd(Expression exp1, int sign1, Expression exp2, int sign2) {
        Double value1 = Double.parseDouble(exp1.asDoubleLiteralExpr().getValue());
        value1 *= sign1;
        Double value2 = Double.parseDouble(exp2.asDoubleLiteralExpr().getValue());
        value2 *= sign2;
        value1 = (value2 + value1) / 2.0;
        return value1;
    }

    private Long getaLongValue(Expression exp1, Operator op1, int sign1) {
        Long value = Long.parseLong(exp1.asIntegerLiteralExpr().getValue());
        value *= sign1;
        if (op1.equals(LESS)) {
            value--;
        } else if (op1.equals(GREATER)) {
            value++;
        }
        return value;
    }

    private Integer getIntegerValue(Expression exp1, Operator op1, int sign1) {
        Integer value = Integer.parseInt(exp1.asIntegerLiteralExpr().getValue());
        value *= sign1;
        if (op1.equals(LESS)) {
            value--;
        } else if (op1.equals(GREATER)) {
            value++;
        }
        return value;
    }

    private Operator invert(Operator op1) {
        if (op1.equals(LESS)) {
            return GREATER;
        } else if (op1.equals(GREATER)) {
            return LESS;
        } else if (op1.equals(GREATER_EQUALS)) {
            return LESS_EQUALS;
        } else {
            return GREATER_EQUALS;
        }
    }

    private static boolean parameterMatch(Parameter parameter) {
        return parameter.getType().isReferenceType();
    }

    private static List<String> findIfNullCheckArgs(CallableDeclaration callableDeclaration) {
        return findIfNullCheckCall(callableDeclaration).stream().map(m -> m.getNameAsString())
                .collect(Collectors.toList());
    }

    private static List<String> findRequireNonNullArgs(CallableDeclaration callableDeclaration) {
        return findMethodsCall(callableDeclaration, CALL_PATTERN).stream()
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
        return node.findAll(IfStmt.class).stream()
                .filter(ifStmt -> checkIAE(ifStmt))
                .flatMap(ifStmt -> ifStmt.findAll(BinaryExpr.class).stream())
                .filter(be -> checkRangeExp(be, params))
                .collect(toList());
    }

    private static boolean checkRangeExp(BinaryExpr bi, List<String> params) {
        if (LESS.equals(bi.getOperator()) || GREATER.equals(bi.getOperator())
                || GREATER_EQUALS.equals(bi.getOperator()) || LESS_EQUALS.equals(bi.getOperator())) {
            return (params.contains(bi.getLeft().toString()) && (bi.getRight().isLiteralStringValueExpr() || bi
                    .getRight().isUnaryExpr()))
                    || (bi.getRight().isUnaryExpr() || (bi.getLeft().isLiteralStringValueExpr()) && params
                    .contains(bi.getRight().toString()));
        }
        return false;
    }

    private static Map<String, List<BinaryExpr>> paramsToBinaryExprMap(List<BinaryExpr> binaryExprs,
            List<String> params) {
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
        return (param.equals(bi.getLeft().toString()) && (bi.getRight().isLiteralStringValueExpr() || bi.getRight()
                .isUnaryExpr()))
                || ((bi.getLeft().isLiteralStringValueExpr() || bi.getRight().isUnaryExpr()) && param
                .equals(bi.getRight().toString()));
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

    private static NameExpr toNameExp(IfStmt ifStmt) {
        return ifStmt.findAll(BinaryExpr.class).stream()
                .map(bi -> (bi.getLeft().isNullLiteralExpr() ? bi.getRight() : bi.getLeft()).asNameExpr())
                .findFirst().get();
    }

    private class ExpressionOperatorSign {

        private BinaryExpr binaryExpr;
        private int sign = 1;
        private Expression expression;
        private Operator operator;

        public ExpressionOperatorSign(BinaryExpr binaryExpr) {
            this.binaryExpr = binaryExpr;
        }

        public Expression getExpression() {
            return expression;
        }

        public int getSign() {
            return sign;
        }

        public Operator getOperator() {
            return operator;
        }

        public ExpressionOperatorSign invoke() {
            if (binaryExpr.getLeft().isLiteralExpr() || binaryExpr.getLeft().isUnaryExpr()) {
                if (binaryExpr.getLeft().isUnaryExpr()) {
                    if (UnaryExpr.Operator.MINUS.equals(binaryExpr.getLeft().asUnaryExpr().getOperator())) {
                        sign = -1;
                    }
                    expression = binaryExpr.getLeft().asUnaryExpr().getExpression();
                } else {
                    expression = binaryExpr.getLeft();
                }
                operator = invert(binaryExpr.getOperator());
            } else {
                if (binaryExpr.getRight().isUnaryExpr()) {
                    if (UnaryExpr.Operator.MINUS.equals(binaryExpr.getRight().asUnaryExpr().getOperator())) {
                        sign = -1;
                    }
                    expression = binaryExpr.getRight().asUnaryExpr().getExpression();
                } else {
                    expression = binaryExpr.getRight();
                }
                operator = binaryExpr.getOperator();
            }
            return this;
        }
    }
}
