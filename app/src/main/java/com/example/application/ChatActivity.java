package com.example.application;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.application.adapters.MessageAdapter;
import com.example.application.models.Message;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView messagesRecyclerView;
    private EditText messageEditText;
    private FloatingActionButton sendButton;
    private TextView chatNameTextView;
    private TextView chatAvatarTextView;
    private ImageButton backButton;
    private ImageButton attachImageButton;

    private MessageAdapter messageAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private ListenerRegistration messagesListener;
    
    private android.os.Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private static final long HEARTBEAT_INTERVAL = 3000; // 3 segundos

    private String chatId;
    private String otherUserId;
    private String otherUserName;
    private String currentUserId;
    private String currentUserName;

    // Launcher para seleccionar imagen de la galería
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUserId = mAuth.getCurrentUser().getUid();

        // Inicializar selector de imágenes
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageAndSendMessage(imageUri);
                        }
                    }
                }
        );

        // Obtener datos del intent
        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("otherUserId");
        otherUserName = getIntent().getStringExtra("otherUserName");

        // Inicializar vistas
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        chatNameTextView = findViewById(R.id.chatNameTextView);
        chatAvatarTextView = findViewById(R.id.chatAvatarTextView);
        backButton = findViewById(R.id.backButton);
        attachImageButton = findViewById(R.id.attachImageButton);

        // Configurar RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Mostrar mensajes desde abajo
        messagesRecyclerView.setLayoutManager(layoutManager);
        messageAdapter = new MessageAdapter(currentUserId);
        messagesRecyclerView.setAdapter(messageAdapter);

        // Cargar nombre del usuario actual
        loadCurrentUserName();

        // Configurar UI
        chatNameTextView.setText(otherUserName);
        chatAvatarTextView.setText(getInitials(otherUserName));

        // Botón de retroceso
        backButton.setOnClickListener(v -> finish());

        // Botón para adjuntar imagen
        attachImageButton.setOnClickListener(v -> openImagePicker());

        // Botón de enviar
        sendButton.setOnClickListener(v -> sendMessage());

        // Cargar mensajes
        loadMessages();
        // Marcar mensajes como leídos inmediatamente
        markAllMessagesAsRead();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Iniciar heartbeat
        startHeartbeat();
        // Marcar mensajes como leídos cada vez que el usuario vuelve a la actividad
        markAllMessagesAsRead();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Detener heartbeat
        stopHeartbeat();
    }
    
    private void startHeartbeat() {
        if (heartbeatHandler == null) {
            heartbeatHandler = new android.os.Handler();
        }
        
        stopHeartbeat();
        
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                updateHeartbeat();
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        
        heartbeatHandler.post(heartbeatRunnable);
    }
    
    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
    }
    
    private void updateHeartbeat() {
        if (currentUserId != null) {
            Map<String, Object> heartbeatData = new HashMap<>();
            heartbeatData.put("online", true);
            heartbeatData.put("lastSeen", com.google.firebase.Timestamp.now());
            
            db.collection("users")
                    .document(currentUserId)
                    .update(heartbeatData)
                    .addOnFailureListener(e -> {
                        db.collection("users")
                                .document(currentUserId)
                                .set(heartbeatData, com.google.firebase.firestore.SetOptions.merge());
                    });
        }
    }
    
    private void setUserOffline() {
        if (currentUserId != null) {
            Map<String, Object> offlineData = new HashMap<>();
            offlineData.put("online", false);
            offlineData.put("lastSeen", com.google.firebase.Timestamp.now());
            
            db.collection("users")
                    .document(currentUserId)
                    .update(offlineData)
                    .addOnFailureListener(e -> {
                        db.collection("users")
                                .document(currentUserId)
                                .set(offlineData, com.google.firebase.firestore.SetOptions.merge());
                    });
        }
    }

    private void loadCurrentUserName() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUserName = documentSnapshot.getString("name");
                    }
                });
    }

    private void loadMessages() {
        messagesListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error al cargar mensajes", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<Message> messages = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Message message = new Message();
                            message.setMessageId(doc.getId());
                            message.setText(doc.getString("text"));
                            message.setSenderId(doc.getString("senderId"));
                            message.setSenderName(doc.getString("senderName"));
                            
                            com.google.firebase.Timestamp timestamp = doc.getTimestamp("timestamp");
                            if (timestamp != null) {
                                message.setTimestamp(timestamp.toDate());
                            }
                            
                            Boolean read = doc.getBoolean("read");
                            message.setRead(read != null ? read : false);
                            
                            // Cargar campos de imagen
                            String type = doc.getString("type");
                            message.setType(type != null ? type : "text");
                            message.setImageUrl(doc.getString("imageUrl"));
                            
                            // Log para depuración
                            android.util.Log.d("ChatActivity", "Mensaje cargado - Type: " + message.getType() + ", ImageUrl: " + message.getImageUrl());
                            
                            messages.add(message);
                        }
                        messageAdapter.updateMessages(messages);
                        if (messages.size() > 0) {
                            // Usar post para asegurar que el RecyclerView se actualice primero
                            final int lastPosition = messageAdapter.getItemCount() - 1;
                            messagesRecyclerView.post(() -> {
                                messagesRecyclerView.smoothScrollToPosition(lastPosition);
                                // Scroll adicional después de 300ms para asegurar que las imágenes se carguen
                                messagesRecyclerView.postDelayed(() -> {
                                    messagesRecyclerView.smoothScrollToPosition(lastPosition);
                                }, 300);
                            });
                        }
                    }
                });
    }
    
    // Marcar todos los mensajes no leídos del otro usuario como leídos
    private void markAllMessagesAsRead() {
        if (chatId == null || otherUserId == null) {
            return;
        }
        
        // Obtener todos los mensajes del otro usuario
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("senderId", otherUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        android.util.Log.d("ChatActivity", "No hay mensajes del otro usuario");
                        return;
                    }
                    
                    // Usar WriteBatch para actualizar múltiples documentos en una sola operación
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    int unreadCount = 0;
                    
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Boolean read = doc.getBoolean("read");
                        // Si el campo 'read' no existe o es false, agregarlo al batch
                        if (read == null || !read) {
                            batch.update(doc.getReference(), "read", true);
                            unreadCount++;
                            android.util.Log.d("ChatActivity", "Agregando al batch mensaje: " + doc.getId());
                        }
                    }
                    
                    // Ejecutar el batch solo si hay mensajes para actualizar
                    if (unreadCount > 0) {
                        final int finalUnreadCount = unreadCount;
                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    android.util.Log.d("ChatActivity", "✅ " + finalUnreadCount + " mensajes marcados como leídos exitosamente");
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("ChatActivity", "❌ Error al marcar mensajes como leídos: " + e.getMessage(), e);
                                    Toast.makeText(this, "Error al marcar mensajes como leídos", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        android.util.Log.d("ChatActivity", "No hay mensajes sin leer");
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ChatActivity", "Error al obtener mensajes: " + e.getMessage(), e);
                });
    }

    private void sendMessage() {
        String messageText = messageEditText.getText().toString().trim();
        
        if (messageText.isEmpty()) {
            return;
        }

        // Crear mensaje
        Map<String, Object> message = new HashMap<>();
        message.put("text", messageText);
        message.put("senderId", currentUserId);
        message.put("senderName", currentUserName != null ? currentUserName : "Usuario");
        message.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        message.put("read", false);
        message.put("type", "text");  // Agregar tipo de mensaje
        message.put("imageUrl", null); // No hay imagen en mensajes de texto

        // Guardar mensaje en Firestore
        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(documentReference -> {
                    // Actualizar el último mensaje en el chat
                    Map<String, Object> chatUpdate = new HashMap<>();
                    chatUpdate.put("lastMessage", messageText);
                    chatUpdate.put("lastMessageTime", com.google.firebase.firestore.FieldValue.serverTimestamp());
                    chatUpdate.put("lastMessageSenderId", currentUserId);

                    db.collection("chats").document(chatId).update(chatUpdate);

                    // Limpiar campo de texto
                    messageEditText.setText("");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al enviar mensaje", Toast.LENGTH_SHORT).show();
                    android.util.Log.e("ChatActivity", "Error al enviar mensaje de texto", e);
                });
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

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadImageAndSendMessage(Uri imageUri) {
        // Mostrar indicador de carga
        sendButton.setEnabled(false);
        attachImageButton.setEnabled(false);

        // Crear referencia única para la imagen
        String imageId = UUID.randomUUID().toString();
        StorageReference imageRef = storage.getReference()
                .child("chat_images")
                .child(currentUserId)
                .child(imageId + ".jpg");

        // Subir imagen
        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    // Obtener URL de descarga
                    imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        sendImageMessage(imageUrl);
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Error al obtener URL de imagen", Toast.LENGTH_SHORT).show();
                        sendButton.setEnabled(true);
                        attachImageButton.setEnabled(true);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al subir imagen", Toast.LENGTH_SHORT).show();
                    sendButton.setEnabled(true);
                    attachImageButton.setEnabled(true);
                });
    }

    private void sendImageMessage(String imageUrl) {
        if (currentUserName == null) {
            Toast.makeText(this, "Cargando información del usuario...", Toast.LENGTH_SHORT).show();
            return;
        }

        android.util.Log.d("ChatActivity", "Enviando mensaje de imagen con URL: " + imageUrl);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("text", "");
        messageData.put("senderId", currentUserId);
        messageData.put("senderName", currentUserName);
        messageData.put("timestamp", com.google.firebase.Timestamp.now());
        messageData.put("read", false);
        messageData.put("type", "image");
        messageData.put("imageUrl", imageUrl);

        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener(documentReference -> {
                    // Actualizar último mensaje del chat
                    Map<String, Object> chatUpdate = new HashMap<>();
                    chatUpdate.put("lastMessage", "📷 Imagen");
                    chatUpdate.put("lastMessageTime", com.google.firebase.Timestamp.now());
                    chatUpdate.put("lastMessageSenderId", currentUserId);

                    db.collection("chats")
                            .document(chatId)
                            .update(chatUpdate);

                    // Habilitar botones nuevamente
                    sendButton.setEnabled(true);
                    attachImageButton.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al enviar imagen", Toast.LENGTH_SHORT).show();
                    sendButton.setEnabled(true);
                    attachImageButton.setEnabled(true);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener heartbeat y marcar offline
        stopHeartbeat();
        setUserOffline();
        if (messagesListener != null) {
            messagesListener.remove();
        }
    }
}
