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

public class HistoryAdapter extends ListAdapter<HistoryListItem, RecyclerView.ViewHolder> {

    public interface OnHistoryClickListener {
        /** Tapping an expression loads it into the expression bar. */
        void onExpressionClick(String expression);
        /** Tapping a result inserts it at the cursor as if typed. */
        void onResultClick(String result);
        /** Long-pressing an expression copies it as plain text. */
        void onExpressionLongClick(String expression);
        /** Long-pressing a result copies it as plain text. */
        void onResultLongClick(String result);
    }

    private OnHistoryClickListener clickListener;

    public HistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnHistoryClickListener(OnHistoryClickListener listener) {
        this.clickListener = listener;
    }

    // ── DiffUtil ─────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<HistoryListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HistoryListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull HistoryListItem a,
                                               @NonNull HistoryListItem b) {
                    if (a.getType() != b.getType()) return false;
                    if (a instanceof HistoryListItem.GroupItem) {
                        return ((HistoryListItem.GroupItem) a).group.getTimestamp()
                                == ((HistoryListItem.GroupItem) b).group.getTimestamp();
                    }
                    if (a instanceof HistoryListItem.DateSeparator) {
                        return ((HistoryListItem.DateSeparator) a).label
                                .equals(((HistoryListItem.DateSeparator) b).label);
                    }
                    return false;
                }

                @Override
                public boolean areContentsTheSame(@NonNull HistoryListItem a,
                                                  @NonNull HistoryListItem b) {
                    if (a instanceof HistoryListItem.GroupItem) {
                        HistoryGroup ga = ((HistoryListItem.GroupItem) a).group;
                        HistoryGroup gb = ((HistoryListItem.GroupItem) b).group;
                        return ga.getSummaryExpression().equals(gb.getSummaryExpression())
                                && ga.getSummaryResult().equals(gb.getSummaryResult())
                                && ga.isExpanded() == gb.isExpanded();
                    }
                    return true; // DateSeparator same label → same contents
                }
            };

    // ── View type dispatch ────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == HistoryListItem.TYPE_DATE) {
            View v = inflater.inflate(R.layout.item_date_separator, parent, false);
            return new DateViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_history, parent, false);
            return new GroupViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HistoryListItem item = getItem(position);
        if (item instanceof HistoryListItem.DateSeparator) {
            ((DateViewHolder) holder).bind((HistoryListItem.DateSeparator) item);
        } else {
            bindGroup((GroupViewHolder) holder,
                    ((HistoryListItem.GroupItem) item).group);
        }
    }

    // ── Group binding ─────────────────────────────────────────────────────────

    private void bindGroup(GroupViewHolder holder, HistoryGroup group) {
        boolean hasMultipleSteps = group.getSteps().size() > 1;

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

        holder.summaryExpression.setText(group.getSummaryExpression());
        holder.summaryResult.setText(group.getSummaryResult());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(group.getTimestamp())));

        holder.summaryExpression.setOnClickListener(
                v -> notifyExpressionClick(group.getSummaryExpression()));
        holder.summaryResult.setOnClickListener(
                v -> notifyResultClick(group.getSummaryResult()));
        holder.summaryExpression.setOnLongClickListener(v -> {
            notifyExpressionLongClick(group.getSummaryExpression()); return true;
        });
        holder.summaryResult.setOnLongClickListener(v -> {
            notifyResultLongClick(group.getSummaryResult()); return true;
        });

        holder.stepsContainer.removeAllViews();
        if (group.isExpanded() && hasMultipleSteps) {
            holder.stepsContainer.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            List<HistoryItem> steps = group.getSteps();
            for (int i = 0; i < steps.size() - 1; i++) {
                HistoryItem step = steps.get(i);
                View stepView = inflater.inflate(
                        R.layout.item_history_step, holder.stepsContainer, false);
                TextView exprView   = stepView.findViewById(R.id.step_expression);
                TextView resultView = stepView.findViewById(R.id.step_result);
                exprView.setText(step.getExpression());
                resultView.setText(step.getResult());
                exprView.setOnClickListener(v -> notifyExpressionClick(step.getExpression()));
                resultView.setOnClickListener(v -> notifyResultClick(step.getResult()));
                exprView.setOnLongClickListener(v -> {
                    notifyExpressionLongClick(step.getExpression()); return true;
                });
                resultView.setOnLongClickListener(v -> {
                    notifyResultLongClick(step.getResult()); return true;
                });
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

    private void notifyExpressionLongClick(String expression) {
        if (clickListener != null) clickListener.onExpressionLongClick(expression);
    }

    private void notifyResultLongClick(String result) {
        if (clickListener != null) clickListener.onResultLongClick(result);
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView chevron, summaryExpression, summaryResult, timestamp;
        LinearLayout stepsContainer;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            chevron           = itemView.findViewById(R.id.history_chevron);
            summaryExpression = itemView.findViewById(R.id.history_expression);
            summaryResult     = itemView.findViewById(R.id.history_result);
            timestamp         = itemView.findViewById(R.id.history_timestamp);
            stepsContainer    = itemView.findViewById(R.id.history_steps_container);
        }
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        TextView dateLabel;

        DateViewHolder(@NonNull View itemView) {
            super(itemView);
            dateLabel = itemView.findViewById(R.id.date_label);
        }

        void bind(HistoryListItem.DateSeparator item) {
            dateLabel.setText(item.label);
        }
    }
}
