package com.example.application;

import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import java.lang.reflect.Field;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private Button registerButton;
    private TextView loginTextView, titleTextView;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ocultar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_register);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        titleTextView = findViewById(R.id.titleTextView);
        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        registerButton = findViewById(R.id.registerButton);
        loginTextView = findViewById(R.id.loginTextView);
        progressBar = findViewById(R.id.progressBar);
        
        // Aplicar gradiente al título
        applyGradientToTitle();
        
        // Forzar cursor negro INMEDIATAMENTE (sin post)
        forceBlackCursorAbsolute(nameEditText);
        forceBlackCursorAbsolute(emailEditText);
        forceBlackCursorAbsolute(passwordEditText);
        forceBlackCursorAbsolute(confirmPasswordEditText);
        
        // También aplicar cuando la vista gane foco
        nameEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                forceBlackCursorAbsolute((EditText) v);
            }
        });
        emailEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                forceBlackCursorAbsolute((EditText) v);
            }
        });
        passwordEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                forceBlackCursorAbsolute((EditText) v);
            }
        });
        confirmPasswordEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                forceBlackCursorAbsolute((EditText) v);
            }
        });

        // Botón de Registro
        registerButton.setOnClickListener(v -> registerUser());

        // Ir a Login
        loginTextView.setOnClickListener(v -> {
            finish(); // Volver a LoginActivity
        });
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyGradientToTitle();
        }
    }
    
    private void applyGradientToTitle() {
        if (titleTextView.getWidth() > 0) {
            Paint paint = titleTextView.getPaint();
            float width = paint.measureText(titleTextView.getText().toString());
            
            Shader textShader = new LinearGradient(0, 0, width, 0,
                    new int[]{
                        0xFF6366F1, // Indigo vibrante
                        0xFF8B5CF6  // Púrpura vibrante
                    },
                    null, Shader.TileMode.CLAMP);
            titleTextView.getPaint().setShader(textShader);
            titleTextView.invalidate();
        }
    }

    private void registerUser() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Validaciones
        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("El nombre es requerido");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("El email es requerido");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("La contraseña es requerida");
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("La contraseña debe tener al menos 6 caracteres");
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Las contraseñas no coinciden");
            return;
        }

        // Mostrar progreso
        progressBar.setVisibility(View.VISIBLE);
        registerButton.setEnabled(false);

        // Crear usuario en Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Registro exitoso, guardar datos adicionales en Firestore
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToFirestore(user.getUid(), name, email);
                        }
                    } else {
                        // Error en el registro
                        progressBar.setVisibility(View.GONE);
                        registerButton.setEnabled(true);
                        String errorMessage = "Error al registrar usuario";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String name, String email) {
        // Crear documento de usuario en Firestore
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email.toLowerCase().trim()); // Normalizar email a minúsculas
        user.put("createdAt", Timestamp.now());
        user.put("online", true); // Usuario en línea al registrarse
        user.put("lastSeen", Timestamp.now()); // Timestamp de última actividad

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    registerButton.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, "Registro exitoso", Toast.LENGTH_SHORT).show();
                    
                    // Ir a MainActivity
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    registerButton.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, "Error al guardar datos: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
    
    // Método ABSOLUTO para forzar cursor negro sin importar modo oscuro
    private void forceBlackCursorAbsolute(EditText editText) {
        try {
            // MÉTODO 1: Crear drawable negro programáticamente
            android.graphics.drawable.GradientDrawable cursorDrawable = new android.graphics.drawable.GradientDrawable();
            cursorDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            cursorDrawable.setSize(dpToPx(2), dpToPx(20));
            cursorDrawable.setColor(0xFF000000); // NEGRO PURO
            
            // MÉTODO 2: Para API 29+, usar setTextCursorDrawable
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                editText.setTextCursorDrawable(cursorDrawable);
            }
            
            // MÉTODO 3: Usar reflexión para acceder a campos internos
            try {
                Field fEditor = TextView.class.getDeclaredField("mEditor");
                fEditor.setAccessible(true);
                Object editor = fEditor.get(editText);
                
                if (editor != null) {
                    Class<?> clazz = editor.getClass();
                    
                    // Intentar diferentes campos según la versión de Android
                    String[] fieldNames = {"mDrawableForCursor", "mCursorDrawable"};
                    for (String fieldName : fieldNames) {
                        try {
                            Field field = clazz.getDeclaredField(fieldName);
                            field.setAccessible(true);
                            if (fieldName.equals("mCursorDrawable")) {
                                field.set(editor, new Drawable[]{cursorDrawable, cursorDrawable});
                            } else {
                                field.set(editor, cursorDrawable);
                            }
                            break;
                        } catch (Exception e) {
                            // Continuar con el siguiente campo
                        }
                    }
                }
            } catch (Exception e) {
                // Si la reflexión falla, continuar con otros métodos
            }
            
            // MÉTODO 4: Establecer el color de selección y highlight (fallback)
            editText.setHighlightColor(0x40000000); // Negro semi-transparente
            editText.setTextColor(0xFF000000); // Asegurar texto negro
            
            // Forzar re-renderizado
            editText.invalidate();
            editText.requestLayout();
            
        } catch (Exception e) {
            android.util.Log.e("RegisterActivity", "Error: " + e.getMessage());
        }
    }
    
    // Convertir dp a pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
