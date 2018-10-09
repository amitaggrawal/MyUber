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
import android.view.View;
import android.widget.Button;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLastLocationRequest;
    private int mRadius = 1;
    private boolean mDriverFound = false;
    private String mDriverFoundId;
    private Button btn_logout, btn_requestUber;
    private LatLng mPickUpLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btn_requestUber = findViewById(R.id.btn_requestUber);

        btn_logout = findViewById(R.id.btn_logout);
        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(CustomerMapActivity.this, MainActivity.class));
                finish();
            }
        });

        btn_requestUber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("customerRequest");

                GeoFire geoFire = new GeoFire(databaseReference);
                geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                //Setting pickup location;

                mPickUpLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                mMap.addMarker(new MarkerOptions().position(mPickUpLocation).title("Pick Up here!"));

                btn_requestUber.setText("Getting your Uber ... ");

                getClosestDriver();

            }
        });
    }

    private void getClosestDriver() {
        //This function will check if there is any driver available within 1 Km or so on distance.
        // Once driver accepts request remove driver from DB.

        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("DriverAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);

        //GeoQuery: to get all drivers details available.
        //radius is in KMs.

        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mPickUpLocation.latitude, mPickUpLocation.longitude), mRadius);
        //This function is called recursively. So to make sure that no previous listener is active remove all listener.
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                // Anytime when any driver is found within the radius this method will be called.
                //Key is id of driver and location is long lat of driver location.

                /*TODO:
                 *At present this logic is based on assumption that the first driver we found in radius will pick the request. And hence all other found will be ignored.
                * Real situation is always different.
                * */
                if (!mDriverFound) {
                    mDriverFound = true;
                    mDriverFoundId = key;
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
            return;
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
        mLastLocationRequest.setInterval(1000);
        mLastLocationRequest.setFastestInterval(1000);
        // It will give you best accuracy a phone can handle. But it will drain more battery.
        // So be careful in its use.
        mLastLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
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
}

