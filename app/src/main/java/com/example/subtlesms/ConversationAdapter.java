package com.example.subtlesms;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

class Conversation {
    private final String contactName;
    private final String lastMessage;
    private final String timestamp;
    private final String sentiment;

    public Conversation(String contactName, String lastMessage, String timestamp, String sentiment) {
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.sentiment = sentiment;
    }

    public String getContactName() { return contactName; }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public String getSentiment() { return sentiment; }
}
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private final List<Conversation> list;

    public ConversationAdapter(List<Conversation> list) {
        this.list = list;
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
        holder.tvSentiment.setText(item.getSentiment());
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