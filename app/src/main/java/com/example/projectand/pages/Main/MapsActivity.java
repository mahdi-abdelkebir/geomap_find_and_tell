package com.example.projectand.pages.Main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.example.projectand.R;
import com.example.projectand.database.FirebaseCategoryHandler;
import com.example.projectand.database.FirebaseMapMarkerHandler;
import com.example.projectand.database.FirebaseUserHandler;
import com.example.projectand.models.Category;
import com.example.projectand.models.MapMarker;
import com.example.projectand.models.User;
import com.example.projectand.pages.Auth.LoginActivity;
import com.example.projectand.pages.modals.CountryDialogFragment;
import com.example.projectand.pages.modals.CreateMarkerFragment;
import com.example.projectand.pages.modals.MarkerViewFragment;
import com.example.projectand.utils.AddressGetter;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnCameraMoveListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.skydoves.powerspinner.IconSpinnerAdapter;
import com.skydoves.powerspinner.IconSpinnerItem;
import com.skydoves.powerspinner.OnSpinnerItemSelectedListener;
import com.skydoves.powerspinner.PowerSpinnerView;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, OnMarkerClickListener, OnMapLongClickListener, OnCameraMoveListener, CountryDialogFragment.DialogListener, AddressGetter.OnAddressGetterFinished, CreateMarkerFragment.OnMarkerAccepted, OnSpinnerItemSelectedListener {

    DialogFragment dialog;
    private FirebaseUserHandler firebaseUserHandler;
    private FirebaseMapMarkerHandler firebaseMapMarkerHandler;
    private SharedPreferences sharedPreferences;

    HashMap<Integer, String> categoryIdMap;
    List<IconSpinnerItem> categoryList;
    private Geocoder geoCoder;
    private GoogleMap googleMap;
    private Boolean setMode = false;

    private Boolean isAuthenticated = false;
    private User currentUser;

    private boolean waitingForPermission = false;
    private LatLng currentLocation;

    private final String DEFAULT_COUNTRY ="Tunisia, Sousse";
    private String USER_COUNTRY;
    private Boolean appearSearch = false;
    private PowerSpinnerView searchBar;

    private FloatingActionButton fab;
    private Toolbar toolbar;
    private HashMap<String, List<MapMarker>> loadedMaps = new HashMap<>();

    private HashMap<String, MapMarker> markerList;
    BitmapDescriptor colorMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences =  this.getSharedPreferences(
                this.getPackageName(), Context.MODE_PRIVATE);

        try {
            setContentView(R.layout.activity_maps);
        } catch (Exception e) {
            Toast.makeText(this, "Sorry, an exception occurred. Please clean your cache.", Toast.LENGTH_LONG).show();
            Intent i = new Intent(MapsActivity.this, HomeActivity.class);
            startActivity(i);
            finish();
        }

        firebaseUserHandler = new FirebaseUserHandler();
        firebaseMapMarkerHandler = new FirebaseMapMarkerHandler();

        isAuthenticated = firebaseUserHandler.isAuthenticated();
        if (isAuthenticated) {
            currentUser = User.getInstance(this);
            if (currentUser == null) {
                firebaseUserHandler.getCurrentUser().addOnSuccessListener(result -> {
                    currentUser = new User(result);
                    User.localizeInstance(this, new User(result));
                    loadView(); // ok 2
                });
            } else {
                loadView(); // ok
            }
        } else {
            loadView(); // not ok
        }
    }

    /*
        LOADING View
     */
    public void loadView() {

        // MARKER BUTTON
        fab = findViewById(R.id.markerBtn);

        if (isAuthenticated) {
            fab.setBackgroundTintList(ContextCompat.getColorStateList(MapsActivity.this, R.color.flag));
        } else {
            fab.setBackgroundTintList(ContextCompat.getColorStateList(MapsActivity.this, R.color.flag_disabled));
            fab.refreshDrawableState();
        }

        fab.setOnClickListener(view -> {
            if (isAuthenticated) {
                if (!categoryList.isEmpty()) {
                    if (currentLocation != null) {
                        setMode = !setMode;
                        if (setMode) {
                            fab.setBackgroundTintList(ContextCompat.getColorStateList(MapsActivity.this, R.color.flag_enabled));
                            Toast.makeText(this, "Select a place on the map.", Toast.LENGTH_SHORT).show();
                        } else {
                            fab.setBackgroundTintList(ContextCompat.getColorStateList(MapsActivity.this, R.color.flag));
                        }
                        fab.refreshDrawableState();
                    } else {
                        Toast.makeText(this, "Please accept the location permission first!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Sorry, there's no category available currently, try again later.", Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(this, "Please authenticate first!", Toast.LENGTH_SHORT).show();
            }
        });

        // LOAD MAP
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        ((SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map)).getMapAsync(this);
    }

    /*
        ACTION MENU
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.actionbar, menu);

        if (isAuthenticated) {
            if (currentUser.getRole() == 0) {
                toolbar.getMenu().findItem(R.id.action_items).setVisible(true);
            }

            toolbar.getMenu().findItem(R.id.action_logout).setVisible(true);
        } else {
            toolbar.getMenu().findItem(R.id.action_login).setVisible(true);
        }

        if (currentLocation != null) {
            toolbar.getMenu().findItem(R.id.action_cloc).setVisible(true);
        }
        return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch(item.getItemId()){
              /*  case R.id.action_search:
                    triggerSearchBar();
                    break;*/
            case R.id.action_goto:
//                Log.e("location: ", locationAddress);
                dialog = new CountryDialogFragment(this, geoCoder);
                dialog.show(getSupportFragmentManager(), "country");
                break;
            case R.id.action_cloc:
                if (currentLocation != null)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 12));
                else
                    Toast.makeText(this, "Please accept the location permission first!", Toast.LENGTH_SHORT).show();

                break;
            case R.id.action_items:
                intent = new Intent(MapsActivity.this, ItemsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_login:
                intent = new Intent(MapsActivity.this, LoginActivity.class);
                startActivity(intent);
                break;
            case R.id.action_logout:
                firebaseUserHandler.signOut();
                intent = new Intent(MapsActivity.this, HomeActivity.class);
                startActivity(intent);
                break;

        }

        return true;
    }

    @Override
    public void onMapReady(GoogleMap gMap) {
        geoCoder = new Geocoder(this);
        googleMap = gMap;
        googleMap.setOnMapLongClickListener(this);

        if (currentUser != null) {
            googleMap.setOnCameraMoveListener(this);
            googleMap.setOnMarkerClickListener(this);
        }

        // GETTING CURRENT LOCATION
        LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        if (locationManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
            } else {
                setCurrentLocation(locationManager);
            }
        }

        // TOOLBAR
        toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);

        // LOADING MARKERS
        searchBar = (PowerSpinnerView) findViewById(R.id.make_marker_categ);
        markerList = new HashMap<>();
        colorMarker = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        load();

        // MOVING CAMERA
        if (currentUser != null) {
            if (currentUser.getLastLocation() != null) {
                // move map to user location
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentUser.getLastLocation(), 12));
            } else {
                // wait for response
                if (currentLocation == null) {
                    waitingForPermission = true;
                } else {
                    // move to location
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 12));
                    // save position
                    firebaseUserHandler.saveLocation(currentLocation);
                }
            }
        } else {
            Bundle extras = this.getIntent().getExtras();
            String country_name;
            if (extras != null && extras.containsKey("country_name")) {
                country_name = extras.getString("country_name");
            } else {
                country_name = DEFAULT_COUNTRY;
            }

            new AddressGetter(1, geoCoder, this).execute(country_name);
        }
    }

    private void load() {
        FirebaseCategoryHandler firebaseCategoryHandler = new FirebaseCategoryHandler();
        // Create an ArrayAdapter using the string array and a default spinner layout
        firebaseCategoryHandler.getAll().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                categoryList = new ArrayList<>();
                categoryIdMap = new HashMap<>();

                int increment = 0;
                for (DataSnapshot item : task.getResult().getChildren()) {
                    Category category = new Category((String) item.child("id").getValue(), (String) item.child("name").getValue());
                    categoryList.add(new IconSpinnerItem(category.getName()));
                    categoryIdMap.put(increment++, category.getId());
                }

                IconSpinnerAdapter iconSpinnerAdapter = new IconSpinnerAdapter(searchBar);
                searchBar.setSpinnerAdapter(iconSpinnerAdapter);
                searchBar.setItems(categoryList);

                Integer previous_category = sharedPreferences.getInt("favourite_category", 0);

                if (categoryList.size() > previous_category)
                    searchBar.selectItemByIndex(previous_category);

                searchBar.setLifecycleOwner(this);
                searchBar.setOnSpinnerItemSelectedListener(this);
            } else {
                Toast.makeText(this, "Couldn't load markers. Please check your internet connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // SET COUNTRY
    public void setCountry(String location, Address address) {
        Toast.makeText(this, location, Toast.LENGTH_SHORT).show();
        LatLng lng = new LatLng(address.getLatitude(), address.getLongitude());
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lng, 12));
    }

    // GET ALL MARKERS
    public void getAllMarkers() {
        LatLng lng = googleMap.getCameraPosition().target;
        String latitude = String.valueOf(lng.latitude), longitude = String.valueOf(lng.longitude);
        new AddressGetter(2, geoCoder, this).execute(latitude, longitude);
    }

    public void getAllMarkers(Address address) {
        toolbar.setTitle(address.getCountryCode() + " - "+ address.getCountryName());
        toolbar.setSubtitle((address.getLocality() == null? "": address.getLocality()));

        Integer categoryId = searchBar.getSelectedIndex();
        String cameraCountry = address.getCountryName();
        String key = cameraCountry + " "+ categoryIdMap.get(categoryId);

        if (!loadedMaps.containsKey(key)) {
            List<MapMarker> loadMap = new ArrayList<>();
            loadedMaps.put(key, loadMap);
            DatabaseReference markers =  firebaseMapMarkerHandler.getAll(cameraCountry, categoryIdMap.get(categoryId));

            markers.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.e("Marker GET", String.valueOf(task.getResult().getChildrenCount()));
                    task.getResult().getChildren().forEach(v -> {
                        MapMarker mapMarker = new MapMarker(v);
                        if (Duration.between(Instant.now(), mapMarker.getTimeCreation()).toHours() < 5) {
                            mapMarker.setMarker(googleMap.addMarker(new MarkerOptions().icon(colorMarker).position(mapMarker.getLocation())));
                            markerList.put(mapMarker.getLocation().latitude + " " + mapMarker.getLocation().longitude, mapMarker);
                            loadMap.add(mapMarker);
                        } else {
                            firebaseMapMarkerHandler.deleteMarker(mapMarker);
                        }
                    });
                } else {
                    Log.e("Marker GET", "ERROR"+ task.getException().getMessage());
                }
            });

            ChildEventListener markerListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    Log.e("Marker", "New marker added");
                    MapMarker mapMarker = new MapMarker(snapshot);
                    mapMarker.setMarker(googleMap.addMarker(new MarkerOptions().icon(colorMarker).position(mapMarker.getLocation())));
                    markerList.put(mapMarker.getLocation().latitude + " " + mapMarker.getLocation().longitude, mapMarker);
                    loadMap.add(mapMarker);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                    Log.e("Marker", "Marker was removed.");
                    if (snapshot.child("location").exists()) {
                        HashMap<String, String> loc = (HashMap<String, String>) snapshot.child("location").getValue();
                        String locationString = loc.get("latitude") + " " + loc.get("longitude");
                        if (markerList.containsKey(locationString)) {
                            markerList.get(locationString).getMarker().remove();
                            markerList.remove(locationString);
                        }
                    }
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            };

            markers.addChildEventListener(markerListener);
        }
    }

    @Override
    public void onMapLongClick(@NonNull LatLng place) {
        Log.e("location", "clicked");

        if (setMode) {
            try {
                if (USER_COUNTRY == null) {
                    List<Address> addresses = geoCoder.getFromLocation(currentLocation.latitude, currentLocation.longitude, 1);
                    if (!addresses.isEmpty()) {
                        USER_COUNTRY = addresses.get(0).getCountryName();
                    }
                }
            // go to details activity with result
                List<Address> addresses = geoCoder.getFromLocation(place.latitude, place.longitude, 1);
                if (!addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    if (Objects.equals(address.getCountryName(), USER_COUNTRY)) {
                        dialog = new CreateMarkerFragment(this, address, searchBar.getSelectedIndex(), categoryList, categoryIdMap);
                        dialog.show(getSupportFragmentManager(), "country");
                        setMode = false;
                        fab.setBackgroundTintList(ContextCompat.getColorStateList(MapsActivity.this, R.color.flag));
                    } else {
                        Toast.makeText(this,"You cannot set marker outside of your country!", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // UNUSED SEARCH BAR ANIMATION
 /*   private void triggerSearchBar() {
        Toast.makeText(this, "Triggered.", Toast.LENGTH_SHORT).show();

        float translationY = !appearSearch ? 130f : 0;
        appearSearch = !appearSearch;

        searchBar.animate()
                .translationY(translationY)
                .setDuration(1000)
                .start();
    }*/



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.e("Location", String.valueOf(requestCode));
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
                setCurrentLocation(locationManager);
            } else {
                Toast.makeText(getBaseContext(), "Permission Not Granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDialogValidated(DialogFragment dialog, String locationAddress, Address address) {
        setCountry(locationAddress, address);
        Log.e("Location", "set to new");
    }

    @Override
    public void onCameraMove() {
        LatLng newLocation = new LatLng(googleMap.getCameraPosition().target.latitude, googleMap.getCameraPosition().target.longitude);
        firebaseUserHandler.saveLocation(newLocation);
        toolbar.setTitle("Loading...");
        toolbar.setSubtitle("");
        getAllMarkers();
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        String locationString = marker.getPosition().latitude + " " + marker.getPosition().longitude;
        if (markerList.containsKey(locationString)) {
            dialog = new MarkerViewFragment(this, markerList.get(locationString), categoryList, searchBar.getSelectedIndex());
            dialog.show(getSupportFragmentManager(), "show");
        }

        return true;
    }

    @Override
    public void onMarkerAccepted(DialogFragment dialog, MapMarker mapMarker, Integer selectedCategoryId) {
        firebaseMapMarkerHandler.createNew(mapMarker);
        if (selectedCategoryId != searchBar.getSelectedIndex()) {
            searchBar.selectItemByIndex(selectedCategoryId);
            changeMarkers(searchBar.getSelectedIndex(), selectedCategoryId);

        } else { // if there's no such country in loaded map, this will reload again
            getAllMarkers();
        }
    }

    @Override
    public void onItemSelected(int oldIndex, @Nullable Object o, int newIndex, Object t1) {
        if (oldIndex != newIndex) {
            changeMarkers(oldIndex, newIndex);

            if (isAuthenticated){
                sharedPreferences.edit().putInt("favourite_category", newIndex).apply();
            }
        }
    }


    public void changeMarkers(Integer oldCategory, Integer newCategory) {
        for (String key: loadedMaps.keySet()) {
            if (key.matches(".*"+oldCategory)) {
                Log.e("CHANGE INVISIBLE", key + " / size: "+ loadedMaps.get(key).size());
                loadedMaps.get(key).forEach(marker -> {
                    Log.e("CHANGE found", marker.getId());
                    marker.getMarker().setVisible(false);
                });
            } else if (key.matches(".*"+newCategory)) {
                Log.e("CHANGE VISIBLE", key + " / size "+ loadedMaps.get(key).size());
                loadedMaps.get(key).forEach(marker -> {
                    marker.getMarker().setVisible(true);
                });
            }
        };

        getAllMarkers();
    }

    // CURRENT LOCATION
    public void setCurrentLocation(LocationManager locationManager) {
        Location cLocation = locationManager
                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (cLocation != null) {
            currentLocation = new LatLng(cLocation.getLatitude(), cLocation.getLongitude());
            Log.e("Location :", "current location");

            googleMap.addMarker(new MarkerOptions().position(currentLocation).title("Current location"));
            if (waitingForPermission) {
                // move to location
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 12));
            }
        }
    }

    @Override
    public void onAddressGetterFinished(Integer code, String result, Address address) {
        if (address != null) {
            if (code == 1) {
                setCountry(result, address);
                System.out.println(result + ": " + address.getLatitude() + ", " + address.getLongitude());
            } else if (code == 2) {
                getAllMarkers(address);
            }
        }
    }
}