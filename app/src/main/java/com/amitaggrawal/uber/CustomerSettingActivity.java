package com.amitaggrawal.uber;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CustomerSettingActivity extends AppCompatActivity {

    EditText editText_Username, editText_phoneNo;
    Button btn_save, btn_cancel;
    ImageView imageView_UserProfile;

    private static final int REQUEST_IMAGE = 1;
    private FirebaseAuth mAuth;
    private DatabaseReference mCustomerInfoDatabase;

    private String mUserId;
    private String mUserName;
    private String mUserPhoneNumber;

    private String mUserProfileImageURI;
    private Uri mChosenImageURI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_setting);

        editText_Username = findViewById(R.id.editText_users_name);
        editText_phoneNo = findViewById(R.id.editText_user_phoneno);

        imageView_UserProfile = findViewById(R.id.imageView_userImage);

        btn_save = findViewById(R.id.btn_save);
        btn_cancel = findViewById(R.id.btn_cancel);

        mAuth = FirebaseAuth.getInstance();
        mUserId = mAuth.getCurrentUser().getUid();

        mCustomerInfoDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(mUserId);
        getUserInformation();

        imageView_UserProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_IMAGE);
            }
        });

        btn_save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUserInformation();
            }
        });

        btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /*Check result code to result_Ok to ensure you got some result.*/
        if (requestCode == REQUEST_IMAGE && resultCode == AppCompatActivity.RESULT_OK) {
            /*We get URI of the chosen image.*/
            final Uri imageUri = data.getData();
            mChosenImageURI = imageUri;

            imageView_UserProfile.setImageURI(mChosenImageURI);
        }
    }

    private void getUserInformation() {
        /*This will show user information if already present.
         * One method is mCustomerInfoDatabase.keepSync(true); It keep cache of everything.
         * */

        mCustomerInfoDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (null != map.get("name")) {
                        mUserName = map.get("name").toString();
                        editText_Username.setText(mUserName);
                    }

                    if (null != map.get("phone")) {
                        mUserPhoneNumber = map.get("phone").toString();
                        editText_phoneNo.setText(mUserPhoneNumber);
                    }

                    if (null != map.get("profileImageUrl")) {
                        mUserProfileImageURI = map.get("profileImageUrl").toString();
                        // using GLIDE to load image.
                        Glide.with(getApplicationContext()).load(mUserProfileImageURI).into(imageView_UserProfile);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void saveUserInformation() {
        mUserName = editText_Username.getText().toString();
        mUserPhoneNumber = editText_phoneNo.getText().toString();

        // Because we are bundling together and saving a bunch of item.
        Map userInfo = new HashMap();
        userInfo.put("name", mUserName);
        userInfo.put("phone", mUserPhoneNumber);

        mCustomerInfoDatabase.updateChildren(userInfo);

        /*save image to firebase*/
        if (null != mChosenImageURI) {
            final StorageReference filePath = FirebaseStorage.getInstance().getReference().child("profile_images").child(mUserId);

            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getApplication().getContentResolver(), mChosenImageURI);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);

            byte[] data = baos.toByteArray();
            // UploadTask is built in firebase upload function for storage.
            final UploadTask uploadTask = filePath.putBytes(data);
            uploadTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e("Image upload", "failed" + e.getMessage());
                    finish();
                }
            });

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    filePath.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            Map newImage = new HashMap();
                            newImage.put("profileImageUrl", uri.toString());
                            mCustomerInfoDatabase.updateChildren(newImage);

                            finish();
                            return;
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            finish();
                            return;
                        }
                    });
                }
            });
        } else {
            finish();
        }

    }
}
