package com.example.subtlesmsBackUp.subtlesms;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.subtlesms.R;

import java.util.List;

class Conversation {
    private String contactName;
    private final String lastMessage;
    private final String timestamp;
    private String sentiment = "";
    private final String threadId;
    private final String address;
    private boolean rando;

    public Conversation(String contactName, String lastMessage, String timestamp, String sentiment, String threadId, String address, boolean rando) {
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.sentiment = sentiment;
        this.threadId = threadId;
        this.address = address;
        this.rando = rando;
    }

    public String getContactName() { return contactName; }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public String getSentiment() { return sentiment; }
    public String getThreadId() { return threadId; }
    public String getAddress() { return address; }
    public boolean getRando() { return rando; }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }
    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }
    public void setRando(boolean isRando) {
        this.rando = isRando;
    }
}





public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Conversation conversation);
    }
    private final List<Conversation> list;
    private final OnItemClickListener listener;

    public ConversationAdapter(List<Conversation> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation item = list.get(position);
        holder.tvName.setText(item.getContactName());
        holder.tvMessage.setText(item.getLastMessage());
        holder.tvTime.setText(item.getTimestamp());

        if (item.getRando()) {
            holder.itemView.setAlpha(0.5f);
        } else {
            holder.itemView.setAlpha(1.0f);
        }

        String sentiment = item.getSentiment();
        if (sentiment != null && !sentiment.trim().isEmpty()) {
            holder.tvSentiment.setText(sentiment);
            holder.tvSentiment.setVisibility(View.VISIBLE);

            Context context = holder.itemView.getContext();
            String key = sentiment.toLowerCase().trim();

            // Dynamically look up resource IDs matching your naming convention
            int bgResId = context.getResources().getIdentifier("sentiment_" + key + "_bg", "color", context.getPackageName());
            int textResId = context.getResources().getIdentifier("sentiment_" + key + "_text", "color", context.getPackageName());

            // Fallback to neutral colors if the specific color resource doesn't exist
            if (bgResId == 0) bgResId = R.color.sentiment_neutral_bg;
            if (textResId == 0) textResId = R.color.sentiment_neutral_text;

            int bgColor = ContextCompat.getColor(context, bgResId);
            int textColor = ContextCompat.getColor(context, textResId);

            ViewCompat.setBackgroundTintList(holder.tvSentiment, ColorStateList.valueOf(bgColor));
            holder.tvSentiment.setTextColor(textColor);

        } else {
            holder.tvSentiment.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
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