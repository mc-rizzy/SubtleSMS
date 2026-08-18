package com.example.subtlesms;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter only - no data loading, no sentiment/auto-reply logic. Message
 * data comes from AppRepository via ChatActivity.
 *
 * Renders a flattened "display list" built from the raw message list, with
 * a date-separator header inserted wherever the day changes. Call refresh()
 * whenever the underlying message list is replaced or appended to, so the
 * separators get recomputed.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DATE_HEADER = 0;
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private static final SimpleDateFormat DAY_LABEL_FORMATTER = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
    private final Date reusableDate = new Date();

    private final List<SmsMessage> messages;
    private final List<Object> displayItems = new ArrayList<>();
    private final Map<Integer, Drawable> drawableCache = new HashMap<>();
    private String searchQuery; // null/blank = normal date-grouped view

    private static class DateHeader {
        final String label;
        DateHeader(String label) { this.label = label; }
    }

    public ChatAdapter(List<SmsMessage> messages) {
        this.messages = messages;
        rebuildDisplayItems();
    }

    /** Rebuilds the date-separator/message layout and re-renders. Call after messages change. */
    public void refresh() {
        rebuildDisplayItems();
        notifyDataSetChanged();
    }

    /**
     * Filters the thread down to messages whose body contains {@code query} (case-insensitive).
     * Pass null/blank to clear the filter and return to the normal date-grouped view.
     */
    public void setSearchQuery(String query) {
        this.searchQuery = (query == null || query.trim().isEmpty()) ? null : query.trim();
        refresh();
    }

    public boolean isSearchActive() {
        return searchQuery != null;
    }

    private void rebuildDisplayItems() {
        displayItems.clear();

        if (searchQuery != null) {
            String lowerQuery = searchQuery.toLowerCase(Locale.US);
            for (SmsMessage msg : messages) {
                if (msg.getBody() != null && msg.getBody().toLowerCase(Locale.US).contains(lowerQuery)) {
                    displayItems.add(msg);
                }
            }
            return;
        }

        Calendar lastDay = null;
        for (SmsMessage msg : messages) {
            Calendar msgDay = Calendar.getInstance();
            msgDay.setTimeInMillis(msg.getTimestamp());

            boolean isNewDay = lastDay == null
                    || lastDay.get(Calendar.YEAR) != msgDay.get(Calendar.YEAR)
                    || lastDay.get(Calendar.DAY_OF_YEAR) != msgDay.get(Calendar.DAY_OF_YEAR);

            if (isNewDay) {
                displayItems.add(new DateHeader(formatDayLabel(msgDay)));
                lastDay = msgDay;
            }
            displayItems.add(msg);
        }
    }

    private String formatDayLabel(Calendar day) {
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (isSameDay(day, today)) return "Today";
        if (isSameDay(day, yesterday)) return "Yesterday";
        return DAY_LABEL_FORMATTER.format(day.getTime());
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayItems.get(position);
        if (item instanceof DateHeader) return TYPE_DATE_HEADER;
        return ((SmsMessage) item).isSent() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_date_separator, parent, false);
            return new DateHeaderViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(viewType == TYPE_SENT ? R.layout.item_message_sent : R.layout.item_message_received, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        Object item = displayItems.get(position);

        if (item instanceof DateHeader) {
            ((DateHeaderViewHolder) rawHolder).tvDateLabel.setText(((DateHeader) item).label);
            return;
        }

        ViewHolder holder = (ViewHolder) rawHolder;
        SmsMessage msg = (SmsMessage) item;
        Context context = holder.itemView.getContext();

        int textResId = msg.isAutomated() ? R.color.AutoReplyColor : R.color.BorderBubbleColor;
        if (holder.tvBody != null) {
            holder.tvBody.setTextColor(ContextCompat.getColor(context, textResId));
        }

        if (holder.tvBody != null) {
            holder.tvBody.setText(msg.getBody());
            holder.tvBody.setAlpha(1.0f);
        }

        if (holder.tvSenderName != null) {
            if (!msg.isSent() && msg.isGroup()) {
                holder.tvSenderName.setVisibility(View.VISIBLE);
                holder.tvSenderName.setText(msg.getSenderName() != null ? msg.getSenderName() : "Unknown");
            } else {
                holder.tvSenderName.setVisibility(View.GONE);
            }
        }

        if (holder.tvTime != null) {
            synchronized (TIME_FORMATTER) {
                reusableDate.setTime(msg.getTimestamp());
                holder.tvTime.setText(TIME_FORMATTER.format(reusableDate));
            }
        }

        if (holder.layoutBubble != null && msg.getStatus() != null) {
            int drawableResId = getDrawableForStatus(msg.getStatus());
            if (drawableResId != 0) {
                Drawable bgDrawable = getCachedDrawable(context, drawableResId);
                if (holder.layoutBubble.getBackground() != bgDrawable) {
                    holder.layoutBubble.setBackground(bgDrawable);
                }
            }

            if (holder.tvBody != null) {
                switch (msg.getStatus()) {
                    case SENT_PENDING:
                    case MANUAL_SENT_FAILED:
                    case AUTO_SENT_FAILED:
                        holder.tvBody.setAlpha(0.5f);
                        break;
                    default:
                        break;
                }
            }
        }

        if (holder.ivMediaContent != null) {
            if (msg.hasMedia() && "image".equalsIgnoreCase(msg.getMediaType())) {
                holder.ivMediaContent.setVisibility(View.VISIBLE);
                Glide.with(context).load(msg.getMediaUri()).centerCrop().into(holder.ivMediaContent);
            } else {
                holder.ivMediaContent.setVisibility(View.GONE);
                holder.ivMediaContent.setImageDrawable(null);
            }
        }
    }

    private int getDrawableForStatus(MessageStatus status) {
        if (status == null) return 0;
        switch (status) {
            case SENT_PENDING: return R.drawable.bg_bubble_sent;
            case MANUAL_SENT_DELIVERED: return R.drawable.bg_bubble_solid_border;
            case AUTO_SENT_DELIVERED: return R.drawable.bg_bubble_dashed_border;
            case MANUAL_SENT_FAILED: return R.drawable.bg_bubble_red_solid_border;
            case AUTO_SENT_FAILED: return R.drawable.bg_bubble_red_dashed_border;
            case I_RECEIVED: return R.drawable.bg_bubble_received;
            default: return 0;
        }
    }

    private Drawable getCachedDrawable(Context context, int resId) {
        if (!drawableCache.containsKey(resId)) {
            Drawable drawable = ContextCompat.getDrawable(context, resId);
            if (drawable != null) drawableCache.put(resId, drawable);
        }
        return drawableCache.get(resId);
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View layoutBubble;
        TextView tvBody, tvTime, tvSenderName;
        ImageView ivMediaContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutBubble = itemView.findViewById(R.id.layoutBubble);
            tvBody = itemView.findViewById(R.id.tvMessageBody);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            ivMediaContent = itemView.findViewById(R.id.ivMediaContent);
        }
    }

    public static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateLabel;

        public DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateLabel = itemView.findViewById(R.id.tvDateLabel);
        }
    }
}