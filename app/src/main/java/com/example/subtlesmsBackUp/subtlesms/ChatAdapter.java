package com.example.subtlesmsBackUp.subtlesms;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.subtlesms.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final Date reusableDate = new Date();

    private final List<SmsMessage> messages;
    private final Map<Integer, Drawable> drawableCache = new HashMap<>();

    public ChatAdapter(List<SmsMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSent() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(viewType == TYPE_SENT ? R.layout.item_message_sent : R.layout.item_message_received, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsMessage msg = messages.get(position);
        Context context = holder.itemView.getContext();

        String colorKey = "BorderBubbleColor";
        if (msg.isAutomated()) {
            colorKey = "AutoReplyColor";
        }
        int textResId = context.getResources().getIdentifier(colorKey, "color", context.getPackageName());
        if (textResId != 0 && holder.tvBody != null) {
            int textColor = ContextCompat.getColor(context, textResId);
            holder.tvBody.setTextColor(textColor);
        }

        if (holder.tvBody != null) {
            holder.tvBody.setText(msg.getBody());
            holder.tvBody.setAlpha(1.0f);
        }

        // Only show sender name if it's an INCOMING message in a GROUP thread
        if (holder.tvSenderName != null) {
            if (!msg.isSent() && msg.isGroup()) {
                holder.tvSenderName.setVisibility(View.VISIBLE);
                String sender = msg.getSenderName();
                holder.tvSenderName.setText(sender != null ? sender : "Unknown");
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

        // Optimized Background and Alpha Assignment
        if (holder.layoutBubble != null && msg.getStatus() != null) {
            int drawableResId = getDrawableForStatus(msg.getStatus());
            if (drawableResId != 0) {
                Drawable bgDrawable = getCachedDrawable(context, drawableResId);
                if (holder.layoutBubble.getBackground() != bgDrawable) {
                    holder.layoutBubble.setBackground(bgDrawable);
                }
            }

            // Apply alpha reductions where required
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

        // Null-safe Media Rendering
        if (holder.ivMediaContent != null) {
            if (msg.hasMedia() && "image".equalsIgnoreCase(msg.getMediaType()) && msg.getMediaUrl() != null) {
                holder.ivMediaContent.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(Uri.parse(msg.getMediaUrl()))
                        .centerCrop()
                        .into(holder.ivMediaContent);
            } else {
                holder.ivMediaContent.setVisibility(View.GONE);
                holder.ivMediaContent.setImageDrawable(null);
            }
        }
    }

    private int getDrawableForStatus(MessageStatus status) {
        if (status == null) return 0;
        switch (status) {
            case SENT_PENDING:
                return R.drawable.bg_bubble_sent;
            case MANUAL_SENT_DELIVERED:
                return R.drawable.bg_bubble_solid_border;
            case AUTO_SENT_DELIVERED:
                return R.drawable.bg_bubble_dashed_border;
            case MANUAL_SENT_FAILED:
                return R.drawable.bg_bubble_red_solid_border;
            case AUTO_SENT_FAILED:
                return R.drawable.bg_bubble_red_dashed_border;
            case I_RECEIVED:
                return R.drawable.bg_bubble_received;
            default:
                return 0;
        }
    }

    private Drawable getCachedDrawable(Context context, int resId) {
        if (!drawableCache.containsKey(resId)) {
            Drawable drawable = ContextCompat.getDrawable(context, resId);
            if (drawable != null) {
                drawableCache.put(resId, drawable);
            }
        }
        return drawableCache.get(resId);
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
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
}