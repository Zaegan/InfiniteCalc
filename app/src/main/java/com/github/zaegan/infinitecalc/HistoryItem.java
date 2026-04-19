package com.github.zaegan.infinitecalc;

public class HistoryItem {
    private final String expression;
    private final String result;

    public HistoryItem(String expression, String result) {
        this.expression = expression;
        this.result = result;
    }

    public String getExpression() { return expression; }
    public String getResult() { return result; }
}
