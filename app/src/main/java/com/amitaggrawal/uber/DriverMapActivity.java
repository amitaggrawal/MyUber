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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, RoutingListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLastLocationRequest;
    private Button btn_logout, btn_setting, btn_rideStatus;
    private boolean isLoggingOut = false;
    //The custome that was assigned to this driver.
    private String mCustomerID = "", mDestination;
    private LatLng mDestinationLatLng = new LatLng(0.0, 0.0);
    private Marker mPickUpLocationMarker;
    private DatabaseReference mAssignedCustomerPickUpLocationRef;
    private ValueEventListener mAssignedCustomerPickUpLocationListener;
    private LinearLayout mCustomerInfo;
    private ImageView mCustomerImage;
    private static final int LOCATION_REQUEST_CODE = 1;
    private TextView mCustomerName, mCustomerPhoneNumber, mCustomerDestination;
    SupportMapFragment mapFragment;

    private int status = 0;

    private ArrayList<Polyline> polylines;
    private int[] COLORS = new int[]{R.color.colorAccent, R.color.colorPrimary, R.color.colorPrimaryDark, R.color.primary_dark_material_light};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        polylines = new ArrayList<>();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

        } else {
            mapFragment.getMapAsync(this);
        }

        mCustomerInfo = findViewById(R.id.customerInfo);
        mCustomerImage = findViewById(R.id.customerProfileImage);
        mCustomerName = findViewById(R.id.customerName);
        mCustomerPhoneNumber = findViewById(R.id.customerPhoneNumber);
        mCustomerDestination = findViewById(R.id.customerDestination);

        btn_setting = findViewById(R.id.btn_setting);
        btn_logout = findViewById(R.id.btn_logout);
        btn_rideStatus = findViewById(R.id.btn_rideStatus);
        btn_rideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (status) {
                    case 1:
                        //driver has started ride. Draw route to destination
                        status = 2;
                        erasePolyLines();
                        if (mDestinationLatLng.latitude != 0.0 && mDestinationLatLng.longitude != 0.0) {
                            getRouteToMarker(mDestinationLatLng);
                        }
                        btn_rideStatus.setText("Drive started");
                        break;
                    case 2:
                        // the driver is on his way to destination with passenger in his car.
                        endRide();
                        break;
                }
            }
        });
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

        btn_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DriverMapActivity.this, DriverSettingActivity.class));
                return;
            }
        });
        getAssignedCustomer();
    }

    private void endRide() {
        btn_rideStatus.setText("picked customer");
        erasePolyLines();
        String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRefernce = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userID).child("customerRequest");
        driverRefernce.removeValue();

        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("customerRequest");
        GeoFire geoFire = new GeoFire(databaseReference);
        geoFire.removeLocation(mCustomerID);

        mCustomerID = "";

        if (null != mAssignedCustomerPickUpLocationRef && null != mAssignedCustomerPickUpLocationListener) {
            mAssignedCustomerPickUpLocationRef.removeEventListener(mAssignedCustomerPickUpLocationListener);
        }

        mCustomerInfo.setVisibility(View.GONE);
        mCustomerName.setText("");
        mCustomerPhoneNumber.setText("");
        mCustomerDestination.setText("Destination -- ");
        mCustomerImage.setImageResource(R.mipmap.ic_launcher_round);


    }

    private void getAssignedCustomer() {
        //Here will have a listner, listening for the changes inside assigned db child.

        String mDriverFoundID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundID).child("customerRequest").child("customerRideId");

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

                    status = 1;
                    mCustomerID = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickUpLocation();
                    getAssignedCustomerDestination();
                    getAssignedCustomerInfo();
                } else {
                    endRide();
                    /*erasePolyLines();
                     *//**
                     * This is called every time a data is removed from parent tag.
                     * NOTE: Here customer has canceled the request. Now Driver will remove the customer ID and Will be available for the service again.
                     *//*
                    mCustomerID = "";

                    if (null != mPickUpLocationMarker) {
                        mPickUpLocationMarker.remove();
                    }

                    if (null != mAssignedCustomerPickUpLocationRef && null != mAssignedCustomerPickUpLocationListener) {
                        mAssignedCustomerPickUpLocationRef.removeEventListener(mAssignedCustomerPickUpLocationListener);
                    }

                    mCustomerInfo.setVisibility(View.GONE);
                    mCustomerName.setText("");
                    mCustomerPhoneNumber.setText("");
                    mCustomerDestination.setText("Destination -- ");
                    mCustomerImage.setImageResource(R.mipmap.ic_launcher_round);*/

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void getAssignedCustomerDestination() {


        String mDriverFoundID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(mDriverFoundID).child("customerRequest");

        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (map.get("destination") != null) {
                        mDestination = map.get("destination").toString();
                        mCustomerDestination.setText("Destination: " + mDestination);
                    } else {
                        //when destination is not set;
                        mCustomerDestination.setText("Destination: --");
                    }

                    Double destinationLat = 0.0;
                    Double destinationLng = 0.0;

                    if (map.get("destinationLat") != null) {
                        destinationLat = Double.valueOf(map.get("destinationLat").toString());
                    }
                    if (map.get("destinationLng") != null) {
                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
                        mDestinationLatLng = new LatLng(destinationLat, destinationLng);
                    }


                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getAssignedCustomerInfo() {


        /*This will show user information if already present.
         * One method is mCustomerInfoDatabase.keepSync(true); It keep cache of everything.
         * */

        mCustomerInfo.setVisibility(View.VISIBLE);
        DatabaseReference mCustomerInfoDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(mCustomerID);
        mCustomerInfoDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.getChildrenCount() > 0) {
                    Map<String, Object> map = (Map<String, Object>) dataSnapshot.getValue();
                    if (null != map.get("name")) {
                        String mUserName = map.get("name").toString();
                        mCustomerName.setText(mUserName);
                    }

                    if (null != map.get("phone")) {
                        String mUserPhoneNumber = map.get("phone").toString();
                        mCustomerPhoneNumber.setText(mUserPhoneNumber);
                    }

                    if (null != map.get("profileImageUrl")) {
                        String mCustomerProfileImage = map.get("profileImageUrl").toString();
                        // using GLIDE to load image.
                        Glide.with(getApplicationContext()).load(mCustomerProfileImage).into(mCustomerImage);
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
                    LatLng pickUpLocation = new LatLng(locationLatitude, locationLongitude);
                    mPickUpLocationMarker = mMap.addMarker(new MarkerOptions().position(pickUpLocation).title("pick up location").icon(BitmapDescriptorFactory.fromResource(R.mipmap.pin_marker)));
                    getRouteToMarker(pickUpLocation);

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getRouteToMarker(LatLng pickUpLocation) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickUpLocation)
                .build();
        routing.execute();

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
            ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);

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
        DatabaseReference driverAvailableReference = FirebaseDatabase.getInstance().getReference("DriverAvailable");
        DatabaseReference driverWorkingReference = FirebaseDatabase.getInstance().getReference("driversWorking");
        DatabaseReference customerRequestReference = FirebaseDatabase.getInstance().getReference().child("Drivers").child(userId).child("customerRequest");
        customerRequestReference.removeValue();
        /**
         * GeoFire: It does not store data normally as we used to by using set().
         * We will pass the reference of DB to GeoFire and it will automatically create its storage mechanism.
         */

        GeoFire geoFire1 = new GeoFire(driverAvailableReference);
        geoFire1.removeLocation(userId);

        GeoFire geoFire = new GeoFire(driverWorkingReference);
        geoFire.removeLocation(userId);
    }

    @Override
    public void onRoutingFailure(RouteException e) {
        // 2500 per day is the limit of free apis
        if (e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Something went wrong. Try again!", Toast.LENGTH_SHORT).show();
        }
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

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        // Draw routes.
        if (polylines.size() > 0) {
            for (Polyline polyline : polylines) {
                polyline.remove();
            }
        }

        polylines = new ArrayList<>();
        //add routes to the map.
        for (int i = 0; i < route.size(); i++) {
            // in case more than 5 alternatives routes are there.
            int colorIndex = i % COLORS.length;
            PolylineOptions polylineOptions = new PolylineOptions();
            polylineOptions.color(getResources().getColor(COLORS[colorIndex]));
            polylineOptions.width(10 + i * 3);
            polylineOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polylineOptions);
            polylines.add(polyline);

            Toast.makeText(this, "Route" + (i * 1) + ": distance = " + route.get(i).getDistanceValue() + ": duration = " + route.get(i).getDurationValue(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {

    }

    private void erasePolyLines() {

        //It erases all lines when cab request is cancel
        for (Polyline polyline : polylines) {
            polyline.remove();
        }

        polylines.clear();
    }
}
