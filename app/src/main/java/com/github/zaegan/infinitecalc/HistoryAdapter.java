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

    public interface OnGroupLongClickListener {
        void onLongClick(HistoryGroup group);
    }

    private OnGroupLongClickListener longClickListener;

    public HistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnGroupLongClickListener(OnGroupLongClickListener listener) {
        this.longClickListener = listener;
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

        holder.summaryExpression.setText(group.getSummaryExpression());
        holder.summaryResult.setText(group.getSummaryResult());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(group.getTimestamp())));

        // Rebuild steps container
        holder.stepsContainer.removeAllViews();
        if (group.isExpanded() && group.getSteps().size() > 1) {
            holder.stepsContainer.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            // Show all steps except the last (already shown in summary row)
            List<HistoryItem> steps = group.getSteps();
            for (int i = 0; i < steps.size() - 1; i++) {
                HistoryItem step = steps.get(i);
                View stepView = inflater.inflate(
                        R.layout.item_history_step, holder.stepsContainer, false);
                ((TextView) stepView.findViewById(R.id.step_expression))
                        .setText(step.getExpression());
                ((TextView) stepView.findViewById(R.id.step_result))
                        .setText(step.getResult());
                holder.stepsContainer.addView(stepView);
            }
        } else {
            holder.stepsContainer.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && group.getSteps().size() > 1) {
                group.setExpanded(!group.isExpanded());
                notifyItemChanged(pos);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(group);
                return true;
            }
            return false;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView summaryExpression, summaryResult, timestamp;
        LinearLayout stepsContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            summaryExpression = itemView.findViewById(R.id.history_expression);
            summaryResult = itemView.findViewById(R.id.history_result);
            timestamp = itemView.findViewById(R.id.history_timestamp);
            stepsContainer = itemView.findViewById(R.id.history_steps_container);
        }
    }
}
