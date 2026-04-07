package com.example.calculator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryAdapter extends ListAdapter<HistoryItem, HistoryAdapter.ViewHolder> {

    public HistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<HistoryItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HistoryItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull HistoryItem a, @NonNull HistoryItem b) {
                    return a.getTimestamp() == b.getTimestamp();
                }

                @Override
                public boolean areContentsTheSame(@NonNull HistoryItem a, @NonNull HistoryItem b) {
                    return a.getExpression().equals(b.getExpression())
                        && a.getResult().equals(b.getResult())
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
        HistoryItem item = getItem(position);

        holder.expressionText.setText(item.getExpression());
        holder.resultText.setText(item.getResult());
        holder.detailResultText.setText(item.getResult());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        holder.timestampText.setText(sdf.format(new Date(item.getTimestamp())));

        holder.detailContainer.setVisibility(item.isExpanded() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                item.setExpanded(!item.isExpanded());
                notifyItemChanged(adapterPosition);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView expressionText, resultText, timestampText, detailResultText;
        View detailContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            expressionText = itemView.findViewById(R.id.history_expression);
            resultText = itemView.findViewById(R.id.history_result);
            timestampText = itemView.findViewById(R.id.history_timestamp);
            detailContainer = itemView.findViewById(R.id.history_detail_container);
            detailResultText = itemView.findViewById(R.id.history_detail_result);
        }
    }
}
