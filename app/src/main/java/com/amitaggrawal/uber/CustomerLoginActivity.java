package com.amitaggrawal.uber;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CustomerLoginActivity extends AppCompatActivity {

    private EditText fieldEmail, fieldPassword;
    private Button btn_login, btn_register;

    private FirebaseAuth mAuth;

    //Firebase auth state listner. Which informs when the auth state changes.
    private FirebaseAuth.AuthStateListener mFirebaseAuthStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_login);

        // Getting the instance of Firebase auth.
        mAuth = FirebaseAuth.getInstance();

        //setting the auth state lister. Like whether logged in or not.
        mFirebaseAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //check for the user state. This variable store the info of current logged in user.
                //It is also called when user is loggout. i.e, every time there is change in state.
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (null != user) {
                    startActivity(new Intent(CustomerLoginActivity.this, MapActivity.class));
                    finish();
                    return;
                }

            }
        };

        fieldEmail = findViewById(R.id.editText_user_email);
        fieldPassword = findViewById(R.id.editText_user_password);

        btn_login = findViewById(R.id.btn_login);
        btn_register = findViewById(R.id.btn_registration);

        btn_register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = fieldEmail.getText().toString();
                final String password = fieldPassword.getText().toString();

                // Firebase built in function, which performs sanity checks and registration.
                //Remember. Sanity check for password is its length should be at least 6. AND for email it should be in proper email format.

                mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(CustomerLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            Toast.makeText(CustomerLoginActivity.this, CustomerLoginActivity.this.getResources().getString(R.string.signup_error), Toast.LENGTH_SHORT).show();
                        } else {

                            // If user creation is successful. Get following info of the user.
                            try {
                                String userID = mAuth.getCurrentUser().getUid();
                                //Create a database reference pointing to drivers.
                                DatabaseReference current_user_db = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(userID);
                                //Setting some value to above reference. It is not used but ensures that values are saved.
                                current_user_db.setValue(true);
                            } catch (NullPointerException nullPointer) {
                                Log.e("CustomerLoginActivity", "Ln:77. Null Pointer");
                            }

                        }
                    }
                });
            }
        });

        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = fieldEmail.getText().toString();
                final String password = fieldPassword.getText().toString();

                //Firebase built in function to perform login.
                mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(CustomerLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            Toast.makeText(CustomerLoginActivity.this, CustomerLoginActivity.this.getResources().getString(R.string.signin_error), Toast.LENGTH_SHORT).show();
                        }

                        // If sign in will be successful. AuthState Listener will be called. So no else block.

                    }
                });
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        //Stating firebase auth state listener. Must be closed in onStop state of the activity.
        mAuth.addAuthStateListener(mFirebaseAuthStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();

        //Removing firebase auth state listener. It was started in onStart() of activity.
        mAuth.removeAuthStateListener(mFirebaseAuthStateListener);
    }
}
