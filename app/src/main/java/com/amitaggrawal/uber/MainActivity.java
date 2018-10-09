package com.amitaggrawal.uber;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private Button btn_driver, btn_customer;
    private FirebaseAuth.AuthStateListener mFirebaseAuthStateListener;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        /*Function below is redirecting to driver map activity for test only. In real time it has to check whether this is driver or user*/
        mFirebaseAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //check for the user state. This variable store the info of current logged in user.
                //It is also called when user is loggout. i.e, every time there is change in state.
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (null != user) {
                    startActivity(new Intent(MainActivity.this, DriverMapActivity.class));
                    finish();
                }
            }
        };

        btn_driver = findViewById(R.id.btn_driver);
        btn_customer = findViewById(R.id.btn_customer);

        btn_driver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DriverLoginActivity.class));
                finish();
            }
        });

        btn_customer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CustomerLoginActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        mAuth.addAuthStateListener(mFirebaseAuthStateListener);

    }

    @Override
    protected void onStop() {
        super.onStop();

        mAuth.removeAuthStateListener(mFirebaseAuthStateListener);
    }
}
