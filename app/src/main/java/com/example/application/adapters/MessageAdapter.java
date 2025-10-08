package com.example.application.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.application.R;
import com.example.application.models.ChatItem;
import com.example.application.models.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_DATE_SEPARATOR = 3;
    private static final int VIEW_TYPE_IMAGE_SENT = 4;
    private static final int VIEW_TYPE_IMAGE_RECEIVED = 5;

    private List<ChatItem> chatItemList;
    private String currentUserId;

    public MessageAdapter(String currentUserId) {
        this.chatItemList = new ArrayList<>();
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem chatItem = chatItemList.get(position);
        
        if (chatItem.getType() == ChatItem.TYPE_DATE_SEPARATOR) {
            return VIEW_TYPE_DATE_SEPARATOR;
        }
        
        Message message = chatItem.getMessage();
        boolean isSent = message.getSenderId().equals(currentUserId);
        boolean isImage = "image".equals(message.getType());
        
        android.util.Log.d("MessageAdapter", "getItemViewType - Position: " + position + 
                ", Type: " + message.getType() + ", isImage: " + isImage + ", isSent: " + isSent);
        
        if (isImage) {
            return isSent ? VIEW_TYPE_IMAGE_SENT : VIEW_TYPE_IMAGE_RECEIVED;
        } else {
            return isSent ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_SENT:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_sent, parent, false);
                return new SentMessageViewHolder(view);
            case VIEW_TYPE_RECEIVED:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_received, parent, false);
                return new ReceivedMessageViewHolder(view);
            case VIEW_TYPE_IMAGE_SENT:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_image_sent, parent, false);
                return new ImageSentViewHolder(view);
            case VIEW_TYPE_IMAGE_RECEIVED:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message_image_received, parent, false);
                return new ImageReceivedViewHolder(view);
            case VIEW_TYPE_DATE_SEPARATOR:
            default:
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_date_separator, parent, false);
                return new DateSeparatorViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem chatItem = chatItemList.get(position);

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(chatItem.getMessage());
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(chatItem.getMessage());
        } else if (holder instanceof ImageSentViewHolder) {
            ((ImageSentViewHolder) holder).bind(chatItem.getMessage());
        } else if (holder instanceof ImageReceivedViewHolder) {
            ((ImageReceivedViewHolder) holder).bind(chatItem.getMessage());
        } else if (holder instanceof DateSeparatorViewHolder) {
            ((DateSeparatorViewHolder) holder).bind(chatItem.getDateText());
        }
    }

    @Override
    public int getItemCount() {
        return chatItemList.size();
    }

    public void updateMessages(List<Message> newMessages) {
        List<ChatItem> items = new ArrayList<>();
        
        for (int i = 0; i < newMessages.size(); i++) {
            Message currentMessage = newMessages.get(i);
            
            // Verificar si necesitamos agregar un separador de fecha
            if (i == 0 || !isSameDay(newMessages.get(i - 1).getTimestamp(), currentMessage.getTimestamp())) {
                String dateText = getDateText(currentMessage.getTimestamp());
                items.add(new ChatItem(dateText, currentMessage.getTimestamp()));
            }
            
            // Agregar el mensaje
            items.add(new ChatItem(currentMessage));
        }
        
        this.chatItemList = items;
        notifyDataSetChanged();
    }
    
    private boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    private String getDateText(Date date) {
        if (date == null) {
            return "";
        }
        
        Calendar messageDate = Calendar.getInstance();
        messageDate.setTime(date);
        
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        
        if (isSameDay(date, today.getTime())) {
            return "Hoy";
        } else if (isSameDay(date, yesterday.getTime())) {
            return "Ayer";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            return sdf.format(date);
        }
    }

    // ViewHolder para mensajes enviados
    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView timeTextView;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }

        public void bind(Message message) {
            messageTextView.setText(message.getText());
            timeTextView.setText(formatTime(message.getTimestamp()));
        }
    }

    // ViewHolder para mensajes recibidos
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView timeTextView;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }

        public void bind(Message message) {
            messageTextView.setText(message.getText());
            timeTextView.setText(formatTime(message.getTimestamp()));
        }
    }

    // ViewHolder para separadores de fecha
    static class DateSeparatorViewHolder extends RecyclerView.ViewHolder {
        TextView dateSeparatorTextView;

        public DateSeparatorViewHolder(@NonNull View itemView) {
            super(itemView);
            dateSeparatorTextView = itemView.findViewById(R.id.dateSeparatorTextView);
        }

        public void bind(String dateText) {
            dateSeparatorTextView.setText(dateText);
        }
    }

    // ViewHolder para imágenes enviadas
    static class ImageSentViewHolder extends RecyclerView.ViewHolder {
        ImageView messageImageView;
        TextView timeTextView;

        public ImageSentViewHolder(@NonNull View itemView) {
            super(itemView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }

        public void bind(Message message) {
            // Cargar imagen con Glide
            String imageUrl = message.getImageUrl();
            android.util.Log.d("MessageAdapter", "Cargando imagen enviada: " + imageUrl);
            
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // Forzar actualización del layout después de cargar la imagen
                            messageImageView.post(() -> messageImageView.requestLayout());
                            return false;
                        }
                    })
                    .into(messageImageView);
            timeTextView.setText(formatTime(message.getTimestamp()));
        }
    }

    // ViewHolder para imágenes recibidas
    static class ImageReceivedViewHolder extends RecyclerView.ViewHolder {
        ImageView messageImageView;
        TextView timeTextView;

        public ImageReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }

        public void bind(Message message) {
            // Cargar imagen con Glide
            String imageUrl = message.getImageUrl();
            android.util.Log.d("MessageAdapter", "Cargando imagen recibida: " + imageUrl);
            
            Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                            // Forzar actualización del layout después de cargar la imagen
                            messageImageView.post(() -> messageImageView.requestLayout());
                            return false;
                        }
                    })
                    .into(messageImageView);
            timeTextView.setText(formatTime(message.getTimestamp()));
        }
    }

    private static String formatTime(Date date) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return sdf.format(date);
    }
}
