package com.example.application;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.application.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private android.os.Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private static final long HEARTBEAT_INTERVAL = 3000; // 3 segundos

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Verificar si el usuario está autenticado
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // No hay usuario autenticado, redirigir a Login
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Ocultar la ActionBar para que la app sea fullscreen
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications, R.id.navigation_profile)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(binding.navView, navController);
        
        // Obtener y guardar el token de FCM
        getFCMToken();
        
        // Solicitar permiso de notificaciones (Android 13+)
        requestNotificationPermission();
        
        // Iniciar heartbeat para mantener online status
        startHeartbeat();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reiniciar heartbeat cuando volvemos a la app
        startHeartbeat();
        Log.d("MainActivity", "onResume - Heartbeat iniciado");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Detener heartbeat solo cuando la Activity ya no es visible
        stopHeartbeat();
        setUserOffline();
        Log.d("MainActivity", "onStop - Heartbeat detenido y usuario offline");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpiar recursos y asegurar que heartbeat esté detenido
        stopHeartbeat();
        heartbeatHandler = null;
        heartbeatRunnable = null;
        Log.d("MainActivity", "onDestroy - Recursos limpiados");
    }
    
    private void startHeartbeat() {
        if (heartbeatHandler == null) {
            heartbeatHandler = new android.os.Handler();
        }
        
        // Detener cualquier heartbeat existente
        stopHeartbeat();
        
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                updateHeartbeat();
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        
        // Ejecutar inmediatamente y luego cada HEARTBEAT_INTERVAL
        heartbeatHandler.post(heartbeatRunnable);
    }
    
    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
    }
    
    private void updateHeartbeat() {
        // Verificar que la Activity aún está activa
        if (isFinishing() || isDestroyed()) {
            Log.d("MainActivity", "Activity finalizando, cancelando heartbeat");
            stopHeartbeat();
            return;
        }
        
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            Map<String, Object> heartbeatData = new HashMap<>();
            heartbeatData.put("online", true);
            heartbeatData.put("lastSeen", com.google.firebase.Timestamp.now());
            
            db.collection("users")
                    .document(userId)
                    .update(heartbeatData)
                    .addOnFailureListener(e -> {
                        // Si falla update, intentar con set+merge
                        if (!isFinishing() && !isDestroyed()) {
                            db.collection("users")
                                    .document(userId)
                                    .set(heartbeatData, com.google.firebase.firestore.SetOptions.merge())
                                    .addOnFailureListener(e2 -> 
                                        Log.e("MainActivity", "❌ Error en heartbeat", e2));
                        }
                    });
        }
    }
    
    private void setUserOffline() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            Map<String, Object> offlineData = new HashMap<>();
            offlineData.put("online", false);
            offlineData.put("lastSeen", com.google.firebase.Timestamp.now());
            
            db.collection("users")
                    .document(userId)
                    .update(offlineData)
                    .addOnFailureListener(e -> {
                        db.collection("users")
                                .document(userId)
                                .set(offlineData, com.google.firebase.firestore.SetOptions.merge());
                    });
        }
    }
    
    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("MainActivity", "Error al obtener FCM token", task.getException());
                        return;
                    }

                    // Obtener el token
                    String token = task.getResult();
                    Log.d("MainActivity", "FCM Token: " + token);
                    
                    // Guardar el token en Firestore
                    saveFCMToken(token);
                });
    }
    
    private void saveFCMToken(String token) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("fcmToken", token);
            tokenData.put("updatedAt", com.google.firebase.Timestamp.now());
            
            db.collection("users")
                    .document(userId)
                    .update(tokenData)
                    .addOnSuccessListener(aVoid -> 
                        Log.d("MainActivity", "Token FCM guardado exitosamente"))
                    .addOnFailureListener(e -> {
                        Log.e("MainActivity", "Error al actualizar token, intentando crear documento", e);
                        // Si el documento no existe, intentar crearlo con set merge
                        db.collection("users").document(userId)
                            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> 
                                Log.d("MainActivity", "Token FCM guardado con merge"))
                            .addOnFailureListener(e2 -> 
                                Log.e("MainActivity", "Error al guardar token con merge", e2));
                    });
        }
    }
    
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                    1001
                );
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        // Configurar el botón de agregar personalizado
        MenuItem addItem = menu.findItem(R.id.action_add);
        if (addItem != null) {
            android.view.View actionView = addItem.getActionView();
            if (actionView != null) {
                android.widget.ImageButton addButton = actionView.findViewById(R.id.action_add_button);
                if (addButton != null) {
                    addButton.setOnClickListener(v -> {
                        // Obtener el fragmento actual y llamar a showAddChatDialog si es HomeFragment
                        NavController navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment_activity_main);
                        if (navController.getCurrentDestination() != null && 
                            navController.getCurrentDestination().getId() == R.id.navigation_home) {
                            androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
                            if (fragment != null) {
                                androidx.fragment.app.Fragment currentFragment = fragment.getChildFragmentManager().getFragments().get(0);
                                if (currentFragment instanceof com.example.application.ui.home.HomeFragment) {
                                    ((com.example.application.ui.home.HomeFragment) currentFragment).showAddChatDialog();
                                }
                            }
                        }
                    });
                }
            }
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.action_add) {
            // Obtener el fragmento actual y llamar a showAddChatDialog si es HomeFragment
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            if (navController.getCurrentDestination() != null && 
                navController.getCurrentDestination().getId() == R.id.navigation_home) {
                androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
                if (fragment != null) {
                    androidx.fragment.app.Fragment currentFragment = fragment.getChildFragmentManager().getFragments().get(0);
                    if (currentFragment instanceof com.example.application.ui.home.HomeFragment) {
                        ((com.example.application.ui.home.HomeFragment) currentFragment).showAddChatDialog();
                    }
                }
            }
            return true;
        } else if (itemId == R.id.action_logout) {
            // Cerrar sesión
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}