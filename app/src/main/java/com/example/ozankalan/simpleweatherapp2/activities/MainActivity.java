package com.example.ozankalan.simpleweatherapp2.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.ozankalan.simpleweatherapp2.R;
import com.example.ozankalan.simpleweatherapp2.models.WeatherMapData;
import com.example.ozankalan.simpleweatherapp2.services.WeatherMapService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.thebrownarrow.permissionhelper.ActivityManagePermission;
import com.thebrownarrow.permissionhelper.PermissionResult;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import eltos.simpledialogfragment.form.Input;
import eltos.simpledialogfragment.form.SimpleFormDialog;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends ActivityManagePermission implements GoogleApiClient.ConnectionCallbacks, SimpleFormDialog.OnDialogResultListener, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String REQUESTING_LOCATION_UPDATES_KEY = "VEWSWAP";
    private static final String LOCATION_KEY = "LOC-KEY";
    private static final String ZIP_DIALOG = "ZIP_ALERT";
    private static final String NO_RESULTS = "NO_RESULTS";
    private static final String ZIP_CODE = "ZIPCODE";
    private static final String CELSIUS = "metric";
    private static final String FAHRENHEIT = "imperial";
    private final String[] permissions = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"};

    @BindView(R.id.location_title)
    TextView mLocationName;
    @BindView(R.id.current_temp)
    TextView mTemp;
    @BindView(R.id.weather_desc)
    TextView mDescip;
    @BindView(R.id.toolbar)
    Toolbar mToolBar;
    @BindView(R.id.forecast_img)
    ImageView mForecastIcon;
    @BindView(R.id.temp_title)
    TextView mTempTitle;

    Unbinder unbinder;
    private SearchView mSearchView;
    private Retrofit retrofit = null;
    private WeatherMapService service = null;
    private WeatherMapData weatherMapData;

    // Google client to interact with Google API
    private GoogleApiClient mGoogleApiClient;

    // boolean flag to toggle periodic location updates
    private boolean mRequestingLocationUpdates = false;

    private Location mLastLocation;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;
    private Bundle savedInstanceState;
    private boolean gpsValid = false;
    private boolean isCelsius = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unbinder = ButterKnife.bind(this);
        setSupportActionBar(mToolBar);
        this.savedInstanceState = savedInstanceState;
        updateValuesFromBundle(savedInstanceState);

        retrofit = new Retrofit.Builder()
                .baseUrl(getResources().getString(R.string.base_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(WeatherMapService.class);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setFastestInterval(600000)
                .setInterval(900000);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        verifyPermissions();
    }


    /**
     * Handle the edge cases for permissions
     */
    private void verifyPermissions() {
        askCompactPermissions(permissions, new PermissionResult() {
            @Override
            public void permissionGranted() {
                //permission granted
                gpsValid = true;
            }

            @Override
            public void permissionDenied() {
                //permission denied
                gpsValid = false;
                clearView();
                alert("");
            }

            @Override
            public void permissionForeverDenied() {
                gpsValid = false;
                clearView();
                alert("");

            }
        });
    }

    /**
     * this method prompt the user to enter a zipcode if permissions were denied
     * or if results return nothing, to try to enter another zip
     */
    private void alert(String alertType) {

        if (alertType.equalsIgnoreCase(NO_RESULTS)) {
            SimpleFormDialog.build()
                    .title(R.string.alert_no_results_title)
                    .msg(R.string.no_results_msg)
                    .fields(
                            Input.plain(ZIP_CODE).max(5)
                                    .inputType(InputType.TYPE_CLASS_NUMBER)
                                    .required()
                                    .hint(R.string.zip_hint)
                    )
                    .cancelable(true)
                    .show(this, ZIP_DIALOG);
        } else {

            SimpleFormDialog.build()
                    .title(R.string.alert_no_permissions_title)
                    .msg(R.string.alert_no_permission_msg)
                    .fields(
                            Input.plain(ZIP_CODE).max(5)
                                    .inputType(InputType.TYPE_CLASS_NUMBER)
                                    .required()
                                    .hint(R.string.zip_hint)
                    )
                    .cancelable(true)
                    .show(this, ZIP_DIALOG);
        }

    }

    /**
     * Method to handle the results from the alert dialogs
     */
    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        if (which == BUTTON_POSITIVE && ZIP_DIALOG.equals(dialogTag)) {
            mSearchView.setQuery(extras.getCharSequence(ZIP_CODE), true);
            return true;
        }
        return false;
    }

    /**
     * Call for when we want to change metrics
     * and update the data with another API call
     *
     * @param switchUnits
     * @return
     */
    private String changeUnits(boolean switchUnits) {
        if (switchUnits)
            return CELSIUS;
        else
            return FAHRENHEIT;
    }

    /**
     * method to get the forecast
     * based on the latest or last known gps
     */
    public void getGeoForecast() {
        if (mCurrentLocation != null) {
            getWeatherGeo(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        } else if (mLastLocation != null) {
            getWeatherGeo(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        }

    }

    /**
     * method to get the forecast based on the entered zipCode
     */
    private WeatherMapData getWeatherZip(int zipCode) {
        Call<WeatherMapData> call = service.getZipWeather(zipCode, changeUnits(isCelsius), getResources().getString(R.string.key));
        call.enqueue(new Callback<WeatherMapData>() {
            @Override
            public void onResponse(Call<WeatherMapData> call, Response<WeatherMapData> response) {
                if (response.body() != null) {
                    setForecastView(response);
                } else {
                    alert(NO_RESULTS);
                }
            }

            @Override
            public void onFailure(Call<WeatherMapData> call, Throwable throwable) {
                Log.e(this.toString(), throwable.toString());
            }
        });

        return weatherMapData;
    }

    /**
     * method to get the forecast based on the location
     */
    private WeatherMapData getWeatherGeo(final double lat, final double lon) {
        Call<WeatherMapData> call = service.getGeoWeather(lat, lon, changeUnits(isCelsius), getResources().getString(R.string.key));
        call.enqueue(new Callback<WeatherMapData>() {
            @Override
            public void onResponse(Call<WeatherMapData> call, Response<WeatherMapData> response) {
                setForecastView(response);
                Log.d(this.toString(), "Number of movies received: " + response.body().getMain().getPressure());
            }

            @Override
            public void onFailure(Call<WeatherMapData> call, Throwable throwable) {
                Log.e(this.toString(), throwable.toString());
            }
        });

        return weatherMapData;
    }

    /**
     * after each successful call from the API
     * we need to update the UI
     */
    private void setForecastView(Response<WeatherMapData> response) {
        weatherMapData = response.body();
        mLocationName.setText(weatherMapData.getName());
        mTemp.setText(String.valueOf(weatherMapData.getMain().getTemp()));
        mDescip.setText(weatherMapData.getWeather().get(0).getDescription());
        mTempTitle.setVisibility(View.VISIBLE);

        Picasso.with(getApplicationContext())
                .load(getString(R.string.base_url)
                        + getString(R.string.icon_url)
                        + weatherMapData.getWeather().get(0).getIcon())
                .memoryPolicy(MemoryPolicy.NO_CACHE)
                .into(mForecastIcon);
    }

    /**
     * clear views
     */
    private void clearView() {
        mLocationName.setText("");
        mDescip.setText("");
        mTemp.setText("");
        mTempTitle.setVisibility(View.INVISIBLE);
        mForecastIcon.setImageResource(0);
    }

    /**
     * to decided if we update via Zipcode or GPS
     */
    private void updateQuery() {
        if (gpsValid) {
            getGeoForecast();
        } else {
            if (!mSearchView.getQuery().equals(""))
                getWeatherZip(Integer.parseInt(mSearchView.getQuery().toString()));
        }
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onResume() {
        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates) {
            startLocationUpdates();
        }
        super.onResume();
    }

    protected void onPause() {

        if (mSearchView != null && !mSearchView.getQuery().equals(""))
            mSearchView.setQuery("", false);

        if (mGoogleApiClient.isConnected() && !mRequestingLocationUpdates)
            stopLocationUpdates();
        super.onPause();
    }

    protected void onStop() {
        stopLocationUpdates();
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    protected void onDestroy() {
        unbinder.unbind();
        retrofit = null;
        service = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(item);
        mSearchView.setInputType(InputType.TYPE_CLASS_NUMBER);
        mSearchView.findViewById(android.support.v7.appcompat.R.id.search_src_text).setBackgroundColor(Color.WHITE);
        ((EditText) mSearchView.findViewById(android.support.v7.appcompat.R.id.search_src_text)).setTextColor(Color.BLACK);
        ((EditText) mSearchView.findViewById(android.support.v7.appcompat.R.id.search_src_text)).setHintTextColor(Color.BLACK);
        setUpSearchViewListeners();


        return true;
    }

    public void setUpSearchViewListeners() {
        // Define the listeners
        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                if (gpsValid) {
                    getGeoForecast();
                    return true;
                } else
                    clearView();
                return false;
            }
        });


        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                clearView();
                if (query.equalsIgnoreCase("") && gpsValid) {
                    getGeoForecast();
                } else {
                    int zip = Integer.parseInt(mSearchView.getQuery().toString());
                    getWeatherZip(zip);
                }

                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                clearView();
                if (query.equalsIgnoreCase("") && gpsValid) {
                    getGeoForecast();
                }
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();


        if (id == R.id.action_edit) {
            isCelsius = !isCelsius;

            if (isCelsius)
                item.setIcon(R.drawable.c_temp);
            else
                item.setIcon(R.drawable.f_temp);

            updateQuery();


        } else if (id == R.id.action_settings) {
            openSettingsApp(getApplicationContext());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            verifyPermissions();
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi
                .getLastLocation(mGoogleApiClient);
        mLastLocation.setElapsedRealtimeNanos(600000);
        getGeoForecast();
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        getGeoForecast();
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY,
                mRequestingLocationUpdates);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and
            // make sure that the Start Updates and Stop Updates buttons are
            // correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

        }
    }
}
