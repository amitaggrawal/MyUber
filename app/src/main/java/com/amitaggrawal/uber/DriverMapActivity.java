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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
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

import java.util.List;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLastLocationRequest;
    private Button btn_logout;
    private boolean isLoggingOut = false;
    //The custome that was assigned to this driver.
    private String mCustomerID = "";
    private Marker mPickUpLocationMarker;
    private DatabaseReference mAssignedCustomerPickUpLocationRef;
    private ValueEventListener mAssignedCustomerPickUpLocationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btn_logout = findViewById(R.id.btn_logout);
        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Firebase built in function to logout user.
                isLoggingOut = true;
                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(DriverMapActivity.this, MainActivity.class));
                finish();
            }
        });

        getAssignedCustomer();
    }

    private void getAssignedCustomer() {
        //Here will have a listner, listening for the changes inside assigned db child.

        String mDriverFoundID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundID).child("customerRideId");

        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                  /*  Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (null != map.get("customerRideId")) {
                        mCustomerID = map.get("customerRideId").toString();

                        //From this customer ID we can get the location of customer from DB.
                        getAssignedCustomerPickUpLocation();
                    }*/

                    mCustomerID = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickUpLocation();
                } else {
                    /**
                     * This is called every time a data is removed from parent tag.
                     * NOTE: Here customer has canceled the request. Now Driver will remove the customer ID and Will be available for the service again.
                     */
                    mCustomerID = "";

                    if (null != mPickUpLocationMarker) {
                        mPickUpLocationMarker.remove();
                    }

                    if (null != mAssignedCustomerPickUpLocationRef && null != mAssignedCustomerPickUpLocationListener) {
                        mAssignedCustomerPickUpLocationRef.removeEventListener(mAssignedCustomerPickUpLocationListener);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void getAssignedCustomerPickUpLocation() {
        mAssignedCustomerPickUpLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child(mCustomerID).child("l");
        mAssignedCustomerPickUpLocationListener = mAssignedCustomerPickUpLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && !mCustomerID.equals("")) {
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLatitude = 0, locationLongitude = 0;

                    if (null != map.get(0)) {
                        locationLatitude = Double.parseDouble(map.get(0).toString());
                    }
                    if (null != map.get(1)) {
                        locationLongitude = Double.parseDouble(map.get(1).toString());
                    }

                    //Put a marker at the pickup location
                    LatLng driverCurrentLocation = new LatLng(locationLatitude, locationLongitude);
                    mPickUpLocationMarker = mMap.addMarker(new MarkerOptions().position(driverCurrentLocation).title("pick up location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pin_marker)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

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
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLastLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onLocationChanged(Location location) {

        if (null != getApplicationContext() && !isLoggingOut) {
            //This function will be called every time after the set interval.

            mLastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            //To move the camera as the person moves.
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            //zoomTo(int index): this can be any index between 1-21.
            //  Different indexes gives different type of zoom. The smaller they are the farther they are to the ground.
            mMap.animateCamera(CameraUpdateFactory.zoomTo(15));


            //Save last know driver location into database.

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            //getReference("abc") differs from getReference().getChild("abc"): as the prior will be createda as the root.
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("DriverAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking");

            GeoFire geoFireDriverAvailable = new GeoFire(refAvailable);
            GeoFire geoFireDriverWorking = new GeoFire(refWorking);

            switch (mCustomerID) {
                case "":
                    /*Case when driver stops working and is avaliable again for next ride*/
                    geoFireDriverWorking.removeLocation(userId);
                    geoFireDriverAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;
                default:
                    /*Case when driver is working i.e, ongoing ride*/
                    geoFireDriverAvailable.removeLocation(userId);
                    geoFireDriverWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;
            }


            /**
             * GeoFire: It does not store data normally as we used to by using set().
             * We will pass the reference of DB to GeoFire and it will automatically create its storage mechanism.
             */


        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    protected void onStop() {
        super.onStop();

        //Anytime driver logout from the activity we will remove the record of the driver from DB.
        //Assuming that driver is not working when his activity is closed.

        try {
            if (!isLoggingOut) {
                disconnectDriver();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disconnectDriver() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        //getReference("abc") differs from getReference().getChild("abc"): as the prior will be createda as the root.
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("DriverAvailable");

        /**
         * GeoFire: It does not store data normally as we used to by using set().
         * We will pass the reference of DB to GeoFire and it will automatically create its storage mechanism.
         */

        GeoFire geoFire = new GeoFire(databaseReference);
        geoFire.removeLocation(userId);
    }
}
