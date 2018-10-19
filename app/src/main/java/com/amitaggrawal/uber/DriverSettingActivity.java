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
import android.widget.RadioButton;
import android.widget.RadioGroup;

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

public class DriverSettingActivity extends AppCompatActivity {

    EditText editText_Username, editText_phoneNo, editText_carInfo;
    Button btn_save, btn_cancel;
    ImageView imageView_UserProfile;

    private static final int REQUEST_IMAGE = 1;
    private FirebaseAuth mAuth;
    private DatabaseReference mDriverDatabaseInfo;

    private String mUserId;
    private String mUserName;
    private String mUserPhoneNumber;
    private String mCarInfo;

    private String mUserProfileImageURI;
    private Uri mChosenImageURI;

    private RadioGroup mRadioGroup;
    private String mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_setting);

        editText_Username = findViewById(R.id.editText_users_name);
        editText_phoneNo = findViewById(R.id.editText_user_phoneno);
        editText_carInfo = findViewById(R.id.editText_car);

        imageView_UserProfile = findViewById(R.id.imageView_userImage);

        mRadioGroup = findViewById(R.id.radioGroup);
        btn_save = findViewById(R.id.btn_save);
        btn_cancel = findViewById(R.id.btn_cancel);

        mAuth = FirebaseAuth.getInstance();
        mUserId = mAuth.getCurrentUser().getUid();

        mDriverDatabaseInfo = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mUserId);
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

        mDriverDatabaseInfo.addValueEventListener(new ValueEventListener() {
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

                    if (null != map.get("car")) {
                        mCarInfo = map.get("car").toString();
                        editText_carInfo.setText(mCarInfo);
                    }

                    if (null != map.get("service")) {
                        mService = map.get("service").toString();
                        switch (mService) {
                            case "UberX":
                                mRadioGroup.check(R.id.UberX);
                                break;
                            case "UberGo":
                                mRadioGroup.check(R.id.UberGo);
                                break;
                            case "UberXL":
                                mRadioGroup.check(R.id.UberXL);
                                break;
                        }
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
        mCarInfo = editText_carInfo.getText().toString();

        int selectedId = mRadioGroup.getCheckedRadioButtonId();

        final RadioButton radioButton = findViewById(selectedId);
        // Check if user has not selected any radio button
        if (radioButton.getText() == null) {
            return;
        }

        mService = radioButton.getText().toString();

        // Because we are bundling together and saving a bunch of item.
        Map userInfo = new HashMap();
        userInfo.put("name", mUserName);
        userInfo.put("phone", mUserPhoneNumber);
        userInfo.put("car", mCarInfo);
        userInfo.put("service", mService);

        mDriverDatabaseInfo.updateChildren(userInfo);

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
                            mDriverDatabaseInfo.updateChildren(newImage);

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
