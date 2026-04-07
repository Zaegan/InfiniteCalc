package com.example.calculator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for calculator history with accordion expand/collapse functionality.
 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<HistoryItem> history;

    public HistoryAdapter(List<HistoryItem> history) {
        this.history = history;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        HistoryItem item = history.get(position);
        
        holder.expressionText.setText(item.getExpression());
        holder.resultText.setText(item.getResult());
        
        // Format timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        holder.timestampText.setText(sdf.format(new Date(item.getTimestamp())));
        
        // Set expanded state
        if (item.isExpanded()) {
            holder.detailContainer.setVisibility(View.VISIBLE);
        } else {
            holder.detailContainer.setVisibility(View.GONE);
        }
        
        // Bug #4 fix: Store position in ViewHolder and use notifyItemChanged with correct position
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                item.setExpanded(!item.isExpanded());
                notifyItemChanged(adapterPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return history.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView expressionText;
        TextView resultText;
        TextView timestampText;
        View detailContainer;

        ViewHolder(View itemView) {
            super(itemView);
            expressionText = itemView.findViewById(R.id.history_expression);
            resultText = itemView.findViewById(R.id.history_result);
            timestampText = itemView.findViewById(R.id.history_timestamp);
            detailContainer = itemView.findViewById(R.id.history_detail_container);
        }
    }
}
