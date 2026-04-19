package com.github.zaegan.infinitecalc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends ListAdapter<HistoryGroup, HistoryAdapter.ViewHolder> {

    public interface OnHistoryClickListener {
        /** Tapping an expression loads it into the expression bar. */
        void onExpressionClick(String expression);
        /** Tapping a result inserts it at the cursor as if typed. */
        void onResultClick(String result);
    }

    private OnHistoryClickListener clickListener;

    public HistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnHistoryClickListener(OnHistoryClickListener listener) {
        this.clickListener = listener;
    }

    private static final DiffUtil.ItemCallback<HistoryGroup> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HistoryGroup>() {
                @Override
                public boolean areItemsTheSame(@NonNull HistoryGroup a, @NonNull HistoryGroup b) {
                    return a.getTimestamp() == b.getTimestamp();
                }

                @Override
                public boolean areContentsTheSame(@NonNull HistoryGroup a, @NonNull HistoryGroup b) {
                    return a.getSummaryExpression().equals(b.getSummaryExpression())
                        && a.getSummaryResult().equals(b.getSummaryResult())
                        && a.isExpanded() == b.isExpanded();
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryGroup group = getItem(position);
        boolean hasMultipleSteps = group.getSteps().size() > 1;

        // ── Chevron (expand/collapse) — only shown when there are earlier steps ──
        if (hasMultipleSteps) {
            holder.chevron.setVisibility(View.VISIBLE);
            holder.chevron.setText(group.isExpanded() ? "▼" : "▶");
            holder.chevron.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    group.setExpanded(!group.isExpanded());
                    notifyItemChanged(pos);
                }
            });
        } else {
            holder.chevron.setVisibility(View.INVISIBLE);
        }

        // ── Summary row ──
        holder.summaryExpression.setText(group.getSummaryExpression());
        holder.summaryResult.setText(group.getSummaryResult());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(group.getTimestamp())));

        holder.summaryExpression.setOnClickListener(
                v -> notifyExpressionClick(group.getSummaryExpression()));
        holder.summaryResult.setOnClickListener(
                v -> notifyResultClick(group.getSummaryResult()));

        // ── Expanded steps (all steps except the last, which is shown in summary) ──
        holder.stepsContainer.removeAllViews();
        if (group.isExpanded() && hasMultipleSteps) {
            holder.stepsContainer.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            List<HistoryItem> steps = group.getSteps();
            for (int i = 0; i < steps.size() - 1; i++) {
                HistoryItem step = steps.get(i);
                View stepView = inflater.inflate(
                        R.layout.item_history_step, holder.stepsContainer, false);
                TextView exprView = stepView.findViewById(R.id.step_expression);
                TextView resultView = stepView.findViewById(R.id.step_result);
                exprView.setText(step.getExpression());
                resultView.setText(step.getResult());
                exprView.setOnClickListener(v -> notifyExpressionClick(step.getExpression()));
                resultView.setOnClickListener(v -> notifyResultClick(step.getResult()));
                holder.stepsContainer.addView(stepView);
            }
        } else {
            holder.stepsContainer.setVisibility(View.GONE);
        }
    }

    private void notifyExpressionClick(String expression) {
        if (clickListener != null) clickListener.onExpressionClick(expression);
    }

    private void notifyResultClick(String result) {
        if (clickListener != null) clickListener.onResultClick(result);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView chevron, summaryExpression, summaryResult, timestamp;
        LinearLayout stepsContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            chevron = itemView.findViewById(R.id.history_chevron);
            summaryExpression = itemView.findViewById(R.id.history_expression);
            summaryResult = itemView.findViewById(R.id.history_result);
            timestamp = itemView.findViewById(R.id.history_timestamp);
            stepsContainer = itemView.findViewById(R.id.history_steps_container);
        }
    }
}
