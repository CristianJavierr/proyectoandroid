package com.example.application.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import com.example.application.R;
import com.example.application.models.Chat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<Chat> chatList;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    public ChatAdapter(OnChatClickListener listener) {
        this.chatList = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chatList.get(position);
        
        // Nombre del usuario
        holder.nameTextView.setText(chat.getOtherUserName() != null ? chat.getOtherUserName() : "Usuario");
        
        // Último mensaje
        if (chat.getLastMessage() != null && !chat.getLastMessage().isEmpty()) {
            holder.lastMessageTextView.setText(chat.getLastMessage());
        } else {
            holder.lastMessageTextView.setText("Nuevo chat");
        }
        
        // Tiempo
        if (chat.getLastMessageTime() != null) {
            holder.timeTextView.setText(getTimeAgo(chat.getLastMessageTime()));
        } else {
            holder.timeTextView.setText("Ahora");
        }
        
        // Avatar con iniciales
        String initials = getInitials(chat.getOtherUserName());
        holder.avatarTextView.setText(initials);
        
        // Color aleatorio para avatar basado en el nombre
        int color = generateColorFromName(chat.getOtherUserName());
        holder.avatarCard.setCardBackgroundColor(color);
        
        // Color de texto oscuro para que se vea sobre fondos claros
        holder.avatarTextView.setTextColor(Color.parseColor("#666666"));
        
        // Badge de mensajes no leídos
        if (chat.getUnreadCount() > 0) {
            holder.unreadBadge.setVisibility(View.VISIBLE);
            // Si hay más de 99 mensajes, mostrar "99+"
            String badgeText = chat.getUnreadCount() > 99 ? "99+" : String.valueOf(chat.getUnreadCount());
            holder.unreadBadge.setText(badgeText);
        } else {
            holder.unreadBadge.setVisibility(View.GONE);
        }
        
        // Indicador de estado en línea
        holder.onlineIndicator.setVisibility(View.VISIBLE);
        if (chat.isOtherUserOnline()) {
            holder.onlineIndicator.setBackgroundResource(R.drawable.bg_online_indicator_active);
        } else {
            holder.onlineIndicator.setBackgroundResource(R.drawable.bg_online_indicator);
        }
        
        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatClick(chat);
            }
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public void updateChats(List<Chat> newChats) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ChatDiffCallback(this.chatList, newChats));
        this.chatList = new ArrayList<>(newChats);
        diffResult.dispatchUpdatesTo(this);
    }
    
    private static class ChatDiffCallback extends DiffUtil.Callback {
        private final List<Chat> oldList;
        private final List<Chat> newList;
        
        public ChatDiffCallback(List<Chat> oldList, List<Chat> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        
        @Override
        public int getOldListSize() {
            return oldList.size();
        }
        
        @Override
        public int getNewListSize() {
            return newList.size();
        }
        
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Comparar por chatId (identificador único)
            return oldList.get(oldItemPosition).getChatId()
                    .equals(newList.get(newItemPosition).getChatId());
        }
        
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Chat oldChat = oldList.get(oldItemPosition);
            Chat newChat = newList.get(newItemPosition);
            
            // Comparar todos los campos que se muestran en el UI
            boolean sameLastMessage = (oldChat.getLastMessage() == null && newChat.getLastMessage() == null) ||
                    (oldChat.getLastMessage() != null && oldChat.getLastMessage().equals(newChat.getLastMessage()));
            
            boolean sameUnreadCount = oldChat.getUnreadCount() == newChat.getUnreadCount();
            
            boolean sameOnlineStatus = oldChat.isOtherUserOnline() == newChat.isOtherUserOnline();
            
            boolean sameName = (oldChat.getOtherUserName() == null && newChat.getOtherUserName() == null) ||
                    (oldChat.getOtherUserName() != null && oldChat.getOtherUserName().equals(newChat.getOtherUserName()));
            
            boolean sameTime = (oldChat.getLastMessageTime() == null && newChat.getLastMessageTime() == null) ||
                    (oldChat.getLastMessageTime() != null && oldChat.getLastMessageTime().equals(newChat.getLastMessageTime()));
            
            return sameLastMessage && sameUnreadCount && sameOnlineStatus && sameName && sameTime;
        }
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) {
            return "U";
        }
        String[] parts = name.trim().split(" ");
        if (parts.length >= 2) {
            return (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
        } else {
            return name.substring(0, Math.min(2, name.length())).toUpperCase();
        }
    }

    private int generateColorFromName(String name) {
        if (name == null || name.isEmpty()) {
            return Color.parseColor("#E8E8E8");
        }
        
        // Lista de colores pastel suaves como en la imagen de diseño
        int[] colors = {
            Color.parseColor("#E8E8E8"), // Gris claro
            Color.parseColor("#E8DEEB"), // Lavanda claro
            Color.parseColor("#F0E8F0"), // Rosa muy claro
            Color.parseColor("#E0E8F0"), // Azul muy claro
            Color.parseColor("#F0F0E8"), // Beige claro
            Color.parseColor("#E8F0E8"), // Verde muy claro
            Color.parseColor("#F0E8E8"), // Melocotón claro
            Color.parseColor("#E8F0F0")  // Cyan muy claro
        };
        
        int hash = name.hashCode();
        return colors[Math.abs(hash) % colors.length];
    }

    private String getTimeAgo(Date date) {
        if (date == null) {
            return "Ahora";
        }
        
        long diff = System.currentTimeMillis() - date.getTime();
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        long hours = TimeUnit.MILLISECONDS.toHours(diff);
        long days = TimeUnit.MILLISECONDS.toDays(diff);
        
        if (minutes < 1) {
            return "Ahora";
        } else if (minutes < 60) {
            return minutes + " min";
        } else if (hours < 24) {
            return hours + " hr";
        } else if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            return sdf.format(date);
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        CardView avatarCard;
        TextView avatarTextView;
        TextView nameTextView;
        TextView lastMessageTextView;
        TextView timeTextView;
        TextView unreadBadge;
        View onlineIndicator;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarCard = itemView.findViewById(R.id.avatarCard);
            avatarTextView = itemView.findViewById(R.id.avatarTextView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            lastMessageTextView = itemView.findViewById(R.id.lastMessageTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            unreadBadge = itemView.findViewById(R.id.unreadBadge);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        }
    }
}
