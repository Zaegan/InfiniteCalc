package com.github.zaegan.infinitecalc;

/**
 * Represents a single history entry in the calculator.
 */
public class HistoryItem {
    private String expression;
    private String result;
    private long timestamp;
    private boolean expanded;

    public HistoryItem(String expression, String result, long timestamp) {
        this.expression = expression;
        this.result = result;
        this.timestamp = timestamp;
        this.expanded = false;
    }

    public String getExpression() {
        return expression;
    }

    public String getResult() {
        return result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
