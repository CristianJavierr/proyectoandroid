package com.example.application.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.application.databinding.FragmentHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.example.application.R;
import com.example.application.ChatActivity;
import com.example.application.adapters.ChatAdapter;
import com.example.application.models.Chat;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private RecyclerView recyclerView;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ChatAdapter chatAdapter;
    private android.os.Handler handler;
    private Runnable refreshRunnable;
    private static final long REFRESH_INTERVAL = 5000; // 5 segundos

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Setup RecyclerView
        recyclerView = binding.chatsRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Setup adapter
        chatAdapter = new ChatAdapter(chat -> {
            // Abrir pantalla de conversación
            android.content.Intent intent = new android.content.Intent(requireContext(), ChatActivity.class);
            intent.putExtra("chatId", chat.getChatId());
            intent.putExtra("otherUserId", getOtherUserId(chat));
            intent.putExtra("otherUserName", chat.getOtherUserName());
            startActivity(intent);
        });
        recyclerView.setAdapter(chatAdapter);

        // Iniciar auto-refresh cada 5 segundos
        startAutoRefresh();
        
        // Setup FloatingActionButton
        binding.fabAddChat.setOnClickListener(v -> showAddChatDialog());
        
        // Setup profile button
        binding.profileButton.setOnClickListener(v -> {
            // Navegar a ProfileFragment
            androidx.navigation.NavController navController = androidx.navigation.Navigation.findNavController(v);
            navController.navigate(R.id.navigation_profile);
        });

        return root;
    }

    public void showAddChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_chat, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame);

        com.google.android.material.textfield.TextInputEditText emailInput = dialogView.findViewById(R.id.emailInput);
        android.widget.Button cancelButton = dialogView.findViewById(R.id.cancelButton);
        android.widget.Button addButton = dialogView.findViewById(R.id.addButton);

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        addButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (!email.isEmpty()) {
                dialog.dismiss();
                searchUserAndCreateChat(email);
            } else {
                Toast.makeText(requireContext(), "Ingresa un email válido", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void searchUserAndCreateChat(String email) {
        // Normalizar el email (convertir a minúsculas y quitar espacios)
        String normalizedEmail = email.toLowerCase().trim();
        
        // Mostrar lo que estamos buscando
        Toast.makeText(requireContext(), "Buscando: " + normalizedEmail, Toast.LENGTH_SHORT).show();
        
        // Primero, intentar listar TODOS los usuarios para debug
        db.collection("users")
            .get()
            .addOnSuccessListener(allUsers -> {
                android.util.Log.d("HomeFragment", "Total usuarios en Firestore: " + allUsers.size());
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : allUsers) {
                    String userEmail = doc.getString("email");
                    String userName = doc.getString("name");
                    android.util.Log.d("HomeFragment", "Usuario encontrado - Email: " + userEmail + ", Nombre: " + userName);
                }
            });
        
        // Buscar usuario en Firestore por email
        db.collection("users")
            .whereEqualTo("email", normalizedEmail)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                android.util.Log.d("HomeFragment", "Resultados de búsqueda: " + queryDocumentSnapshots.size());
                
                if (!queryDocumentSnapshots.isEmpty()) {
                    // Usuario encontrado
                    String otherUserId = queryDocumentSnapshots.getDocuments().get(0).getId();
                    String otherUserName = queryDocumentSnapshots.getDocuments().get(0).getString("name");
                    String currentUserId = mAuth.getCurrentUser().getUid();
                    
                    android.util.Log.d("HomeFragment", "Usuario encontrado - ID: " + otherUserId + ", Nombre: " + otherUserName);
                    
                    // Verificar que no sea el mismo usuario
                    if (otherUserId.equals(currentUserId)) {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(requireContext(), "No puedes crear un chat contigo mismo", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    // Crear o buscar chat existente
                    createOrGetChat(currentUserId, otherUserId, otherUserName);
                } else {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(requireContext(), "Usuario no encontrado. Email buscado: " + normalizedEmail, Toast.LENGTH_LONG).show();
                    }
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("HomeFragment", "Error al buscar: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Error al buscar usuario: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
    }

    private void createOrGetChat(String currentUserId, String otherUserId, String otherUserName) {
        // Verificar si ya existe un chat entre estos usuarios
        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Verificar que el fragment sigue activo
                if (!isAdded() || getContext() == null) {
                    return;
                }
                
                String chatId = null;
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    java.util.List<String> participants = (java.util.List<String>) doc.get("participants");
                    if (participants != null && participants.contains(otherUserId)) {
                        chatId = doc.getId();
                        break;
                    }
                }

                if (chatId != null) {
                    Toast.makeText(requireContext(), "Chat con " + otherUserName + " ya existe", Toast.LENGTH_SHORT).show();
                    loadChats();
                } else {
                    // Crear nuevo chat
                    createNewChat(currentUserId, otherUserId, otherUserName);
                }
            });
    }

    private void createNewChat(String currentUserId, String otherUserId, String otherUserName) {
        java.util.Map<String, Object> chat = new java.util.HashMap<>();
        chat.put("participants", java.util.Arrays.asList(currentUserId, otherUserId));
        chat.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        chat.put("lastMessage", "");
        chat.put("lastMessageTime", com.google.firebase.firestore.FieldValue.serverTimestamp());
        chat.put("lastMessageSenderId", "");

        db.collection("chats")
            .add(chat)
            .addOnSuccessListener(documentReference -> {
                // Verificar que el fragment sigue activo
                if (!isAdded() || getContext() == null) {
                    return;
                }
                Toast.makeText(requireContext(), "Chat con " + otherUserName + " creado", Toast.LENGTH_SHORT).show();
                loadChats();
            })
            .addOnFailureListener(e -> {
                // Verificar que el fragment sigue activo
                if (!isAdded() || getContext() == null) {
                    return;
                }
                Toast.makeText(requireContext(), "Error al crear chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void loadChats() {
        // Verificar que el fragment aún está activo
        if (!isAdded() || getContext() == null) {
            Log.d("HomeFragment", "Fragment no está activo, cancelando loadChats");
            return;
        }
        
        String currentUserId = mAuth.getCurrentUser().getUid();
        
        db.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Verificar que el fragment sigue activo
                if (!isAdded() || getContext() == null || binding == null) {
                    return;
                }
                
                if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                    List<Chat> chatList = new ArrayList<>();
                    int[] pendingTasks = {queryDocumentSnapshots.size()};
                    
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Chat chat = new Chat();
                        chat.setChatId(doc.getId());
                        chat.setParticipants((List<String>) doc.get("participants"));
                        chat.setLastMessage(doc.getString("lastMessage"));
                        chat.setLastMessageSenderId(doc.getString("lastMessageSenderId"));
                        
                        // Convertir Timestamp a Date
                        com.google.firebase.Timestamp timestamp = doc.getTimestamp("lastMessageTime");
                        if (timestamp != null) {
                            chat.setLastMessageTime(timestamp.toDate());
                        }
                        
                        // Obtener información del otro usuario
                        List<String> participants = chat.getParticipants();
                        String otherUserId = participants.get(0).equals(currentUserId) 
                                ? participants.get(1) : participants.get(0);
                        
                        chat.setOtherUserId(otherUserId);
                        
                        // Cargar datos del usuario y estado online
                        db.collection("users").document(otherUserId)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                // Verificar que el fragment sigue activo
                                if (!isAdded() || getContext() == null || binding == null) {
                                    return;
                                }
                                
                                if (userDoc.exists()) {
                                    chat.setOtherUserName(userDoc.getString("name"));
                                    chat.setOtherUserEmail(userDoc.getString("email"));
                                    
                                    // Determinar si el usuario está realmente online usando timestamp
                                    boolean isOnline = isUserReallyOnline(userDoc);
                                    chat.setOtherUserOnline(isOnline);
                                }
                                
                                // Contar mensajes no leídos
                                db.collection("chats")
                                    .document(chat.getChatId())
                                    .collection("messages")
                                    .whereEqualTo("senderId", otherUserId)
                                    .whereEqualTo("read", false)
                                    .get()
                                    .addOnSuccessListener(messagesSnapshot -> {
                                        // Verificar que el fragment sigue activo antes de actualizar UI
                                        if (!isAdded() || getContext() == null || binding == null) {
                                            return;
                                        }
                                        
                                        chat.setUnreadCount(messagesSnapshot.size());
                                        
                                        chatList.add(chat);
                                        pendingTasks[0]--;
                                        
                                        if (pendingTasks[0] == 0) {
                                            // Ordenar por fecha del último mensaje
                                            chatList.sort((c1, c2) -> {
                                                if (c1.getLastMessageTime() == null) return 1;
                                                if (c2.getLastMessageTime() == null) return -1;
                                                return c2.getLastMessageTime().compareTo(c1.getLastMessageTime());
                                            });
                                            
                                            chatAdapter.updateChats(chatList);
                                            binding.emptyTextView.setVisibility(View.GONE);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        // Verificar que el fragment sigue activo
                                        if (!isAdded() || getContext() == null || binding == null) {
                                            return;
                                        }
                                        
                                        chat.setUnreadCount(0);
                                        chatList.add(chat);
                                        pendingTasks[0]--;
                                        
                                        if (pendingTasks[0] == 0) {
                                            chatList.sort((c1, c2) -> {
                                                if (c1.getLastMessageTime() == null) return 1;
                                                if (c2.getLastMessageTime() == null) return -1;
                                                return c2.getLastMessageTime().compareTo(c1.getLastMessageTime());
                                            });
                                            
                                            chatAdapter.updateChats(chatList);
                                            binding.emptyTextView.setVisibility(View.GONE);
                                        }
                                    });
                            })
                            .addOnFailureListener(e -> {
                                // Verificar que el fragment sigue activo
                                if (!isAdded() || getContext() == null || binding == null) {
                                    return;
                                }
                                
                                // Si falla cargar usuario, usar valores por defecto
                                chat.setOtherUserName("Usuario");
                                chat.setOtherUserEmail("");
                                chat.setOtherUserOnline(false);
                                chat.setUnreadCount(0);
                                
                                chatList.add(chat);
                                pendingTasks[0]--;
                                
                                if (pendingTasks[0] == 0) {
                                    chatList.sort((c1, c2) -> {
                                        if (c1.getLastMessageTime() == null) return 1;
                                        if (c2.getLastMessageTime() == null) return -1;
                                        return c2.getLastMessageTime().compareTo(c1.getLastMessageTime());
                                    });
                                    
                                    chatAdapter.updateChats(chatList);
                                    binding.emptyTextView.setVisibility(View.GONE);
                                }
                            });
                    }
                } else {
                    // No hay chats - verificar fragment activo antes de actualizar UI
                    if (isAdded() && getContext() != null && binding != null) {
                        chatAdapter.updateChats(new ArrayList<>());
                        binding.emptyTextView.setVisibility(View.VISIBLE);
                    }
                }
            })
            .addOnFailureListener(e -> {
                // Verificar que el fragment sigue activo
                if (!isAdded() || getContext() == null || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(), "Error al cargar chats", Toast.LENGTH_SHORT).show();
            });
    }
    
    private void startAutoRefresh() {
        // Verificar que el fragment está activo antes de iniciar
        if (!isAdded() || getContext() == null) {
            Log.d("HomeFragment", "Fragment no activo, no se inicia auto-refresh");
            return;
        }
        
        if (handler == null) {
            handler = new android.os.Handler();
        }
        
        // Detener cualquier refresh existente
        stopAutoRefresh();
        
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // Verificar que el fragment sigue activo antes de cada refresh
                if (isAdded() && getContext() != null && binding != null) {
                    loadChats();
                    handler.postDelayed(this, REFRESH_INTERVAL);
                } else {
                    Log.d("HomeFragment", "Fragment inactivo, deteniendo auto-refresh");
                }
            }
        };
        handler.post(refreshRunnable);
    }
    
    private void stopAutoRefresh() {
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
    
    /**
     * Determina si un usuario está realmente online basándose en:
     * 1. El campo "online" debe ser true
     * 2. El lastSeen debe ser reciente (menos de 10 segundos)
     * 
     * Con heartbeat cada 3 segundos, un usuario activo nunca excederá 10 segundos.
     * Esto previene que usuarios aparezcan como "online" permanentemente.
     */
    private boolean isUserReallyOnline(com.google.firebase.firestore.DocumentSnapshot userDoc) {
        // Verificar campo online
        Boolean online = userDoc.getBoolean("online");
        if (online == null || !online) {
            return false;
        }
        
        // Verificar timestamp lastSeen
        com.google.firebase.Timestamp lastSeen = userDoc.getTimestamp("lastSeen");
        if (lastSeen == null) {
            return false;
        }
        
        // Calcular diferencia en segundos
        long currentTimeMillis = System.currentTimeMillis();
        long lastSeenMillis = lastSeen.toDate().getTime();
        long differenceSeconds = (currentTimeMillis - lastSeenMillis) / 1000;
        
        // Usuario está online solo si lastSeen fue actualizado en los últimos 10 segundos
        // (heartbeat cada 3 seg + margen para latencia de red)
        boolean isRecent = differenceSeconds < 10;
        
        if (!isRecent) {
            Log.d("HomeFragment", "👤 Usuario con online=true pero lastSeen hace " + differenceSeconds + "s → OFFLINE");
        }
        
        return isRecent;
    }

    private String getOtherUserId(com.example.application.models.Chat chat) {
        String currentUserId = mAuth.getCurrentUser().getUid();
        List<String> participants = chat.getParticipants();
        if (participants != null && participants.size() == 2) {
            return participants.get(0).equals(currentUserId) 
                    ? participants.get(1) 
                    : participants.get(0);
        }
        return "";
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reiniciar auto-refresh cuando volvemos al fragmento
        if (binding != null) {
            Log.d("HomeFragment", "onResume - Iniciando auto-refresh");
            startAutoRefresh();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Detener auto-refresh cuando salimos del fragmento
        Log.d("HomeFragment", "onPause - Deteniendo auto-refresh");
        stopAutoRefresh();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Detener auto-refresh
        stopAutoRefresh();
        
        binding = null;
    }
}