package com.example.locationpicker

import android.R
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.locationpicker.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
                .setAction("Action", null).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}


private fun getLastLocation() {
    if (checkPermissions()) {
        /**
         * check if location is enabled
         */
        if (isLocationEnabled()) {
            /**
             * getting last location from FusedLocationClient object
             */
            if ((ActivityCompat.checkSelfPermission(
                    this@Maoin,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) && (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED)
            ) {
                return
            }
            client.getLastLocation().addOnCompleteListener(OnCompleteListener { task ->
                val location: Location = task.result
                if (location == null) {
                    requestNewLocationData()
                } else {
                    userCurrentLocation = location
                    startLocationService()
                }
            })
        } else {
            buildAlertMessageNoGps()
        }
    } else {
        /**
         * if permissions aren't available, request for permissions
         */
        requestPermissions()
    }
}


/**
 * Ask for GPS Location and get current location
 */
private fun buildAlertMessageNoGps() {
    val locationRequest: LocationRequest = LocationRequest.create()
    locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    locationRequest.interval = 30 * 1000
    locationRequest.fastestInterval = 5 * 1000
    val builder = LocationSettingsRequest.Builder()
        .addLocationRequest(locationRequest)
    builder.setAlwaysShow(true) //this is the key ingredient
    val result: Task<LocationSettingsResponse> =
        LocationServices.getSettingsClient(this)
            .checkLocationSettings(builder.build())
    result.addOnCompleteListener { task ->
        try {
            val response: LocationSettingsResponse = task.getResult(ApiException::class.java)
            /**
             * All location settings are satisfied. The client can initialize location requests here.
             */
        } catch (exception: ApiException) {
            when (exception.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->                       // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                    try {
                        // Cast to a resolvable exception.
                        val resolvable = exception as ResolvableApiException
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        resolvable.startResolutionForResult(
                            this@AddBusinessActivity,
                            REQUEST_CHECK_SETTINGS
                        )
                    } catch (e: SendIntentException) {
                        // Ignore the error.
                    } catch (e: ClassCastException) {
                        // Ignore, should be an impossible error.
                    }
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {}
            }
        }
    }
}


// initializing fusedAPI callback
private val mLocationCallback: LocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        val mLastLocation: Location? = locationResult.lastLocation
        userCurrentLocation = mLastLocation
        startLocationService()
    }
}

/**
 * if now last location is found request new coordinates
 */
@SuppressLint("MissingPermission")
private fun requestNewLocationData() {
    // Initializing LocationRequest
    // object with appropriate methods
    val mLocationRequest = LocationRequest()
    mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    mLocationRequest.interval = 5
    mLocationRequest.fastestInterval = 0
    mLocationRequest.numUpdates = 1
    // setting LocationRequest
    // on FusedLocationClient
    client = LocationServices.getFusedLocationProviderClient(this)
    client.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
}

/**
 * Method to check for permissions
 */
private fun checkPermissions(): Boolean {
    return ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    // If we want background location
    // on Android 10.0 and higher,
    // use:
    // ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
}

/**
 * Method to request for permissions
 */
private fun requestPermissions() {
    requestPermissions(
        this,
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ),
        PERMISSION_ID
    )
}

/**
 * Method to check if location is enabled
 * @return true || false
 */
private fun isLocationEnabled(): Boolean {
    val locationManager = getSystemService<Any>(Context.LOCATION_SERVICE) as LocationManager?
    return locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
        LocationManager.NETWORK_PROVIDER
    )
}

/**
 * If everything is all right in Location permission then getLastLocation
 */
fun onRequestPermissionsResult(
    requesCode: Int,
    permissions: Array<String?>?,
    @NonNull grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode === PERMISSION_ID) {
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation()
        } else {
            myApp.showSnackBarForErrors(this, getResources().getString(R.string.permission_denied))
        }
    }
}


/**
 * GPS permission on ActivityResult
 */
fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    when (requestCode) {
        REQUEST_CHECK_SETTINGS -> when (resultCode) {
            Activity.RESULT_OK ->                     // All required changes were successfully made
                getLastLocation()
            Activity.RESULT_CANCELED ->                     // The user was asked to change settings, but chose not to
                myApp.showSnackBarForErrors(this, getResources().getString(R.string.gps_denied))
            else -> {}
        }
    }
}


/**
 * If every permission is satisfied open the dialog and load map,
 * and set the marker at the user's current location
 */
private fun startLocationService() {
    val b: ActivityShoplocationDialogBinding =
        ActivityShoplocationDialogBinding.inflate(LayoutInflater.from(this@AddBusinessActivity))
    val dialog = Dialog(this@AddBusinessActivity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(b.getRoot())
    dialog.show()
    MapsInitializer.initialize(this@AddBusinessActivity)
    b.mapView.onCreate(dialog.onSaveInstanceState())
    b.mapView.onResume()
    b.mapView.getMapAsync(object : OnMapReadyCallback() {
        @SuppressLint("MissingPermission", "PotentialBehaviorOverride")
        fun onMapReady(googleMap: GoogleMap) {
            // storing location to temporary variable
            val latLng = LatLng(
                userCurrentLocation.getLatitude(),
                userCurrentLocation.getLongitude()
            ) //your lat lng
            val marker: Marker = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.long_press_marker))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            // Enable GPS marker in Map
            googleMap.setMyLocationEnabled(true)
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
            googleMap.getUiSettings().setZoomControlsEnabled(true)
            googleMap.animateCamera(CameraUpdateFactory.zoomTo(15), 1000, null)
            googleMap.setOnCameraMoveListener(object : OnCameraMoveListener() {
                fun onCameraMove() {
                    val midLatLng: LatLng = googleMap.getCameraPosition().target
                    if (marker != null) {
                        marker.setPosition(midLatLng)
                        nowLocation = marker.getPosition()
                    }
                }
            })
        }
    })
    dialog.setCancelable(false)
    b.saveLocation.setOnClickListener(object : OnClickListener() {
        fun onClick(v: View?) {
            if (nowLocation != null) {
                // if user location is null set the previous location fetched
                location = GeoPoint(nowLocation.latitude, nowLocation.longitude)
            } else {
                location =
                    GeoPoint(userCurrentLocation.getLatitude(), userCurrentLocation.getLongitude())
            }
        }
    })
}

private fun getAddressText(location: GeoPoint): String? {
    var addresses: List<Address>? = null
    val geocoder = Geocoder(this@AddBusinessActivity, Locale.getDefault())
    try {
        addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1)
        // Here 1 represent max location result to returned, by documents it recommended 1 to 5
    } catch (e: IOException) {
        e.printStackTrace()
    }
    assert(addresses != null)
    return addresses!![0].getAddressLine(0)
}