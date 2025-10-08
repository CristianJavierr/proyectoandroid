package com.example.application.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.application.ChatActivity;
import com.example.application.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "chat_messages";
    private static final String CHANNEL_NAME = "Mensajes de Chat";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuevo FCM token: " + token);
        
        // Guardar el token en Firestore
        saveFCMTokenToFirestore(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        Log.d(TAG, "Mensaje recibido de: " + remoteMessage.getFrom());

        // Verificar si el mensaje tiene datos
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Datos del mensaje: " + remoteMessage.getData());
            
            String title = remoteMessage.getData().get("title");
            String body = remoteMessage.getData().get("body");
            String chatId = remoteMessage.getData().get("chatId");
            String otherUserId = remoteMessage.getData().get("otherUserId");
            String otherUserName = remoteMessage.getData().get("otherUserName");
            String messageType = remoteMessage.getData().get("messageType");
            
            sendNotification(title, body, chatId, otherUserId, otherUserName, messageType);
        }

        // Verificar si el mensaje tiene notificación
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Cuerpo de la notificación: " + remoteMessage.getNotification().getBody());
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            
            sendNotification(title, body, null, null, null, "text");
        }
    }

    private void sendNotification(String title, String body, String chatId, String otherUserId, String otherUserName, String messageType) {
        // Crear intent para abrir el chat cuando se toque la notificación
        Intent intent;
        if (chatId != null && otherUserId != null && otherUserName != null) {
            intent = new Intent(this, ChatActivity.class);
            intent.putExtra("chatId", chatId);
            intent.putExtra("otherUserId", otherUserId);
            intent.putExtra("otherUserName", otherUserName);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            intent = new Intent(this, com.example.application.MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        // Ajustar el body si es una imagen
        if ("image".equals(messageType)) {
            body = "📷 " + body;
        }

        // Construir la notificación
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body));

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Usar un ID único para cada notificación basado en el chatId o timestamp
        int notificationId = chatId != null ? chatId.hashCode() : (int) System.currentTimeMillis();
        
        if (notificationManager != null) {
            notificationManager.notify(notificationId, notificationBuilder.build());
        }
    }

    private void createNotificationChannel() {
        // Crear el canal de notificación solo para Android O y superiores
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notificaciones de mensajes nuevos");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void saveFCMTokenToFirestore(String token) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String userId = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("fcmToken", token);
            tokenData.put("updatedAt", com.google.firebase.Timestamp.now());

            db.collection("users")
                    .document(userId)
                    .update(tokenData)
                    .addOnSuccessListener(aVoid -> 
                        Log.d(TAG, "Token FCM guardado exitosamente"))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al guardar token FCM", e);
                        // Si el documento no existe, crearlo
                        db.collection("users").document(userId).set(tokenData)
                            .addOnSuccessListener(aVoid2 -> 
                                Log.d(TAG, "Token FCM guardado en nuevo documento"))
                            .addOnFailureListener(e2 -> 
                                Log.e(TAG, "Error al crear documento con token", e2));
                    });
        }
    }
}
