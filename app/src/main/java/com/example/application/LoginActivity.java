package com.example.application;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
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

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private TextView registerTextView, titleTextView;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ocultar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_login);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Verificar si el usuario ya está autenticado
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Usuario ya autenticado, ir a MainActivity
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Inicializar vistas
        titleTextView = findViewById(R.id.titleTextView);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        registerTextView = findViewById(R.id.registerTextView);
        progressBar = findViewById(R.id.progressBar);
        
        // Aplicar gradiente al título
        applyGradientToTitle();
        
        // Forzar cursor negro INMEDIATAMENTE (sin post)
        forceBlackCursorAbsolute(emailEditText);
        forceBlackCursorAbsolute(passwordEditText);
        
        // También aplicar cuando la vista gane foco
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

        // Botón de Login
        loginButton.setOnClickListener(v -> loginUser());

        // Ir a Registro
        registerTextView.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
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

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validaciones
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

        // Mostrar progreso
        progressBar.setVisibility(View.VISIBLE);
        loginButton.setEnabled(false);

        // Iniciar sesión con Firebase
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    loginButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        // Login exitoso
                        Toast.makeText(LoginActivity.this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // Error en el login
                        String errorMessage = "Error al iniciar sesión";
                        if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
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
            android.util.Log.e("LoginActivity", "Error: " + e.getMessage());
        }
    }
    
    // Convertir dp a pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
