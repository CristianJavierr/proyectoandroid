package com.example.application.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.application.LoginActivity;
import com.example.application.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private TextView profileNameTextView, profileEmailTextView, profileAvatarTextView;
    private Button logoutButton;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_profile, container, false);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        profileAvatarTextView = root.findViewById(R.id.profileAvatarTextView);
        profileNameTextView = root.findViewById(R.id.profileNameTextView);
        profileEmailTextView = root.findViewById(R.id.profileEmailTextView);
        logoutButton = root.findViewById(R.id.logoutButton);
        android.widget.ImageButton backButton = root.findViewById(R.id.backButton);

        // Cargar datos del usuario
        loadUserData();

        // Botón de cerrar sesión
        logoutButton.setOnClickListener(v -> logout());
        
        // Botón de retroceso
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        return root;
    }

    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            String email = currentUser.getEmail();

            // Mostrar email
            if (email != null) {
                profileEmailTextView.setText(email);
            }

            // Cargar nombre desde Firestore
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.isEmpty()) {
                                profileNameTextView.setText(name);
                                // Mostrar iniciales en el avatar
                                String initials = getInitials(name);
                                profileAvatarTextView.setText(initials);
                            }
                        }
                    });
        }
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "U";
        
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        } else {
            return name.substring(0, Math.min(2, name.length())).toUpperCase();
        }
    }

    private void logout() {
        mAuth.signOut();
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
