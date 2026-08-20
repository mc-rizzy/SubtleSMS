package com.example.subtlesms;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter only - no data model, no data loading. See Conversation.java for
 * the row model and AppRepository.java for where the data comes from.
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Conversation conversation);
    }

    private List<Conversation> list = new ArrayList<>();
    private final OnItemClickListener listener;

    public ConversationAdapter(List<Conversation> list, OnItemClickListener listener) {
        updateData(list);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    public void updateData(List<Conversation> newList) {
        list.clear();
        for (Conversation convo : newList) {
            if (!convo.getArchived()) {
                list.add(convo);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation item = list.get(position);
        holder.tvName.setText(item.getConversationName());
        holder.tvMessage.setText(item.getLastMessage());
        holder.tvTime.setText(item.getTimestamp());

        holder.itemView.setAlpha(item.getRando() ? 0.5f : 1.0f);

        String sentiment = item.getSentiment();

        if (sentiment != null && !sentiment.trim().isEmpty()) {
            holder.tvSentiment.setText(sentiment);
            holder.tvSentiment.setVisibility(View.VISIBLE);

            Context context = holder.itemView.getContext();
            int bgResId;
            int textResId;
            switch (sentiment.trim().toUpperCase(Locale.US)) {
                case SentimentAnalyzer.POSITIVE:
                    bgResId = R.color.sentiment_positive_bg;
                    textResId = R.color.sentiment_positive_text;
                    break;
                case SentimentAnalyzer.NEGATIVE:
                    bgResId = R.color.sentiment_negative_bg;
                    textResId = R.color.sentiment_negative_text;
                    break;
                default:
                    bgResId = R.color.sentiment_neutral_bg;
                    textResId = R.color.sentiment_neutral_text;
                    break;
            }

            int bgColor = ContextCompat.getColor(context, bgResId);
            int textColor = ContextCompat.getColor(context, textResId);

            ViewCompat.setBackgroundTintList(holder.tvSentiment, ColorStateList.valueOf(bgColor));
            holder.tvSentiment.setTextColor(textColor);
        } else {
            holder.tvSentiment.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvMessage, tvTime, tvSentiment;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvContactName);
            tvMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvTimestamp);
            tvSentiment = itemView.findViewById(R.id.tvSentimentBadge);
        }
    }
}