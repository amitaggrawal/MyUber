package com.amitaggrawal.uber;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final int LOCATION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLastLocationRequest;
    private int mRadius = 1;
    private boolean mDriverFound = false;
    private String mDriverFoundId;
    private Button btn_logout, btn_requestUber, btn_cancelUberRequest, btn_setting;
    private LatLng mPickUpLocation;
    private Marker mDriverMarker, mPickUpMarker;
    private boolean requestedCab = false;
    private GeoQuery geoQuery;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationListener;
    private String mDestination;
    private String TAG = CustomerMapActivity.class.getCanonicalName();
    SupportMapFragment mapFragment;

    private TextView mDriverName, mDriverMobileNumber, mDriverCar;
    private LinearLayout mDriverInfo;
    private ImageView mDriverImage;

    private LatLng mDestinationLatLang;

    private RadioGroup mRadioGroup;
    private String requestService;

    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

        } else {
            mapFragment.getMapAsync(this);
        }

        mDestinationLatLang = new LatLng(0.0, 0.0);
        mDriverInfo = findViewById(R.id.driverInfo);
        mDriverImage = findViewById(R.id.driverProfileImage);
        mDriverName = findViewById(R.id.driverName);
        mDriverMobileNumber = findViewById(R.id.driverPhoneNumber);
        mDriverCar = findViewById(R.id.driverCar);

        mRadioGroup = findViewById(R.id.radioGroup);
        mRadioGroup.check(R.id.UberGo);

        btn_requestUber = findViewById(R.id.btn_requestUber);
        btn_cancelUberRequest = findViewById(R.id.btn_cancelUberRequest);
        btn_setting = findViewById(R.id.btn_setting);
        btn_logout = findViewById(R.id.btn_logout);
        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                disconnectUser();
                startActivity(new Intent(CustomerMapActivity.this, MainActivity.class));
                finish();
            }
        });


        btn_requestUber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int selectedId = mRadioGroup.getCheckedRadioButtonId();

                RadioButton radioButton = findViewById(selectedId);
                // Check if user has not selected any radio button
                if (radioButton.getText() == null) {
                    return;
                }

                requestService = radioButton.getText().toString();

                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("customerRequest");

                GeoFire geoFire = new GeoFire(databaseReference);
                geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                //Setting pickup location;
                //Sometimes pickup marker are getting duplicate. Make them singleton to solve the problem.
                mPickUpLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mPickUpMarker = mMap.addMarker(new MarkerOptions().position(mPickUpLocation).title("Pick Up here!").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pin_marker)));

                btn_requestUber.setText("Getting your Uber ... ");

                requestedCab = true;
                btn_cancelUberRequest.setVisibility(View.VISIBLE);
                btn_requestUber.setEnabled(false);

                getClosestDriver();


            }
        });

        btn_cancelUberRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endRide();
                btn_cancelUberRequest.setVisibility(View.GONE);
                btn_requestUber.setEnabled(true);
                btn_requestUber.setText(getResources().getString(R.string.btn_requestUber));
            }

        });

        btn_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(CustomerMapActivity.this, CustomerSettingActivity.class));
                return;
            }
        });


        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // TODO: Get info about the selected place.
                mDestination = place.getName().toString();
                mDestinationLatLang = place.getLatLng();
                Log.i(TAG, "Place: " + place.getName());
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);
            }
        });
    }

    private void disconnectUser() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        //getReference("abc") differs from getReference().getChild("abc"): as the prior will be createda as the root.
        DatabaseReference customerRequestReference = FirebaseDatabase.getInstance().getReference("customerRequest");
        DatabaseReference customerRequestDriverReference = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundId).child("customerRequest");
        customerRequestDriverReference.removeValue();

        /**
         * GeoFire: It does not store data normally as we used to by using set().
         * We will pass the reference of DB to GeoFire and it will automatically create its storage mechanism.
         */

        GeoFire geoFire1 = new GeoFire(customerRequestReference);
        geoFire1.removeLocation(userId);

    }

    private void removeRequestFromDatabase() {
        /**
         * 1. Remove data from Customer Request.
         * 2. Remove customer id from Driver. Which was denoting that the driver is serving to that customer
         * 3. Maker the driver available again.
         * 4. Remove the marker for the pickup location.
         */

        if (null != mDriverFoundId) {

            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundId).child("customerRequest");
            driverRef.removeValue();

            mDriverFoundId = null;
        }

        mDriverFound = false;
        mRadius = 1;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(databaseReference);
        geoFire.removeLocation(userId);

        if (null != mDriverMarker) {
            mDriverMarker.remove();
        }

        if (null != mPickUpMarker) {
            mPickUpMarker.remove();
        }

        mDriverInfo.setVisibility(View.GONE);
        mDriverName.setText("");
        mDriverMobileNumber.setText("");
        mDriverCar.setText("Destination -- ");
        mDriverImage.setImageResource(R.mipmap.ic_launcher_round);

    }


    private void getClosestDriver() {
        //This function will check if there is any driver available within 1 Km or so on distance.
        // Once driver accepts request remove driver from DB.

        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("DriverAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);

        //GeoQuery: to get all drivers details available.
        //radius is in KMs.

        geoQuery = geoFire.queryAtLocation(new GeoLocation(mPickUpLocation.latitude, mPickUpLocation.longitude), mRadius);
        //This function is called recursively. So to make sure that no previous listener is active remove all listener.
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {

                // Anytime when any driver is found within the radius this method will be called.

                //Key is id of driver and location is long lat of driver location.

                //GeoQuery will run faster than other listners
                /*TODO:
                 *At present this logic is based on assumption that the first driver we found in radius will pick the request. And hence all other found will be ignored.
                 * Real situation is always different.
                 * */
                if (!mDriverFound) {

                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                                Map<String, Object> driverMap = (Map<String, Object>) dataSnapshot.getValue();
                                if (mDriverFound) {
                                    //this is to end the listener if driver is already found. Since there will be many drivers.
                                    return;
                                }

                                /*Customer will be able to choose service.*/
                                if (driverMap.get("service").equals(requestService)) {
                                    mDriverFound = true;
                                    mDriverFoundId = dataSnapshot.getKey();

                                    //Notify driver about pickup request. The person driver has to attend to.

                                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundId).child("customerRequest");
                                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                                    HashMap hashMap = new HashMap();
                                    hashMap.put("customerRideId", customerId);
                                    hashMap.put("customerDestination", mDestination);
                                    hashMap.put("customerDestinationLatitude", mDestinationLatLang.latitude);
                                    hashMap.put("customerDestinationLongitude", mDestinationLatLang.longitude);
                                    driverRef.updateChildren(hashMap);

                                    //Get driver location for customer
                                    btn_requestUber.setText("Looking for near by drivers!");
                                    getDriverLocation();
                                    getDriverInfo();
                                    getCabRideEnded();

                                }

                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {

                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String key) {

                //I assume if a driver moves out of our radius this will be called.
                // The car should be removed from the map or can be there but out of radius.
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

                //This will be called when the query of searching driver will be completed.
                if (!mDriverFound && mRadius < 3) {
                    mRadius += 1;
                    getClosestDriver();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }

    private void getDriverInfo() {

        /*This will show user information if already present.
         * One method is mCustomerInfoDatabase.keepSync(true); It keep cache of everything.
         * */

        mDriverInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerInfoDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundId);
        mCustomerInfoDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (null != map.get("name")) {
                        mDriverName.setText(map.get("name").toString());
                    }

                    if (null != map.get("phone")) {
                        mDriverMobileNumber.setText(map.get("phone").toString());
                    }

                    if (null != map.get("profileImageUrl")) {

                        // using GLIDE to load image.
                        Glide.with(getApplicationContext()).load(map.get("profileImageUrl").toString()).into(mDriverImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getDriverLocation() {
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking").child(mDriverFoundId).child("l");
        driverLocationListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // This is to check that some data exists. Else app will crash. Also if cab has been requested.
                if (dataSnapshot.exists() && requestedCab) {
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLatitude = 0, locationLongitude = 0;
                    btn_requestUber.setText("Ride Found!");
                    if (null != map.get(0)) {
                        locationLatitude = Double.parseDouble(map.get(0).toString());
                    }
                    if (null != map.get(1)) {
                        locationLongitude = Double.parseDouble(map.get(1).toString());
                    }
                    //Put a marker at the drivers position
                    LatLng driverCurrentLocation = new LatLng(locationLatitude, locationLongitude);
                    if (null != driverCurrentLocation) {
                        if (null != mDriverMarker)
                            mDriverMarker.remove();
                    }

                    /*Distance between driver and pickup location*/
                    Location location = new Location("");
                    location.setLatitude(mPickUpLocation.latitude);
                    location.setLongitude(mPickUpLocation.longitude);

                    Location location2 = new Location("");
                    location2.setLatitude(driverCurrentLocation.latitude);
                    location2.setLongitude(driverCurrentLocation.longitude);

                    float distance = location.distanceTo(location2);

                    /*Notify the user that cab has arrived*/
                    if (distance < 100) {
                        btn_requestUber.setText("Driver's here");
                    } else {
                        btn_requestUber.setText("Driver Found" + String.valueOf(distance));
                    }
                    //btn_requestUber.setText("Driver found " + String.valueOf(distance));
                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverCurrentLocation).title("Your driver is here!").icon(BitmapDescriptorFactory.fromResource(R.mipmap.cab_marker)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getCabRideEnded() {
        //Here will have a listner, listening for the changes inside assigned db child.
        driveHasEndedRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundId).child("customerRequest").child("customerRideId");

        driverLocationListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                } else {
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void endRide() {
        requestedCab = false;
        if (null != geoQuery)
            geoQuery.removeAllListeners();

        if (null != driverLocationRef && null != driverLocationListener) {
            driverLocationRef.removeEventListener(driverLocationListener);
        }
        if (null != driveHasEndedRefListener) {
            driveHasEndedRef.removeEventListener(driverLocationListener);
        }
        removeRequestFromDatabase();


    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

        /*// Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));*/
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //Creating a request to get location every second;

        mLastLocationRequest = new LocationRequest();
        // Will get location after every 1 sec.
        mLastLocationRequest.setInterval(5000);
        mLastLocationRequest.setFastestInterval(5000);
        // It will give you best accuracy a phone can handle. But it will drain more battery.
        // So be careful in its use.
        mLastLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

        }

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLastLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {

        //This function will be called every time after the set interval.

        mLastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        //To move the camera as the person moves.
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        //zoomTo(int index): this can be any index between 1-21.
        //  Different indexes gives different type of zoom. The smaller they are the farther they are to the ground.
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));


      /*  //Save last know driver location into database.

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        //getReference("abc") differs from getReference().getChild("abc"): as the prior will be createda as the root.
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("DriverAvailable");

        *//**
         * GeoFire: It does not store data normally as we used to by using set().
         * We will pass the reference of DB to GeoFire and it will automatically create its storage mechanism.
         *//*

        GeoFire geoFire = new GeoFire(databaseReference);
        geoFire.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));*/

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //You make an intent for the permission and give it a number.
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: //The user has allowed us to use location permission.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mapFragment.getMapAsync(this);
                } else {
                    Toast.makeText(this, "Please provide the location permission", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

}

