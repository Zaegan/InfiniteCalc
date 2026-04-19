package com.github.zaegan.infinitecalc;

import java.util.List;

/**
 * A group of HistoryItems accumulated between AC presses.
 * Each item represents one = press; the last item is the summary shown collapsed.
 */
public class HistoryGroup {
    private final List<HistoryItem> steps;
    private final long timestamp;
    private boolean expanded;

    public HistoryGroup(List<HistoryItem> steps, long timestamp) {
        this.steps = steps;
        this.timestamp = timestamp;
        this.expanded = false;
    }

    public List<HistoryItem> getSteps() { return steps; }
    public long getTimestamp() { return timestamp; }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public String getSummaryExpression() {
        return steps.isEmpty() ? "" : steps.get(steps.size() - 1).getExpression();
    }

    public String getSummaryResult() {
        return steps.isEmpty() ? "" : steps.get(steps.size() - 1).getResult();
    }
}
