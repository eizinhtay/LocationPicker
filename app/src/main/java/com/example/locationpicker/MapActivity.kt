package com.example.locationpicker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.locationpicker.databinding.ActivityMapBinding
import com.example.locationpicker.databinding.LocationDialogBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import java.util.*


class MapActivity : AppCompatActivity(), GoogleMap.OnMapClickListener,
    GoogleMap.OnMarkerClickListener, GoogleMap.OnMapLongClickListener {
    private val PERMISSION_ID = 1
    private val REQUEST_CHECK_SETTINGS =100
    private var nowLocation: LatLng ?=null

    var userCurrentLocation: Location? = null
    private lateinit var client: FusedLocationProviderClient
    private lateinit  var placesClient: PlacesClient

    private lateinit var binding: ActivityMapBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Places.initialize(applicationContext, getString(R.string.Api_key))
        placesClient = Places.createClient(this)
        client = LocationServices.getFusedLocationProviderClient(this)

        binding.pickLocation.setOnClickListener {
            getLastLocation()
        }


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
        ActivityCompat.requestPermissions(
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
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    /**
     * If everything is all right in Location permission then getLastLocation
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode === PERMISSION_ID) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            } else {
                // myApp.showSnackBarForErrors(this, resources.getString(R.string.permission_denied))
                Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show()
            }
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
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                client?.lastLocation?.addOnCompleteListener { task ->
                    val location: Location = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        userCurrentLocation = location
                        startLocationService()
                    }
                }
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


    private fun buildAlertMessageNoGps() {
        val locationRequest: LocationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 30 * 1000
        locationRequest.fastestInterval = 5 * 1000
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        builder.setAlwaysShow(true) //this is the key ingredient
        val result: Task<LocationSettingsResponse> =
            LocationServices.getSettingsClient(this@MapActivity)
                .checkLocationSettings(builder.build())
        result.addOnCompleteListener { task ->
            try {
                val response: LocationSettingsResponse = task.getResult(ApiException::class.java)
                /**
                 * All location settings are satisfied. The client can initialize location requests here.
                 */
            } catch (exception: ApiException) {
                when (exception.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->
                        // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                        try {
                            // Cast to a resolvable exception.
                            val resolvable = exception as ResolvableApiException
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            resolvable.startResolutionForResult(
                                this@MapActivity,
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
        client?.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    private fun startLocationService() {
        val b: LocationDialogBinding =
            LocationDialogBinding.inflate(LayoutInflater.from(this@MapActivity))
        val dialog = Dialog(this@MapActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(b.root)
        dialog.show()
        MapsInitializer.initialize(this@MapActivity)
        b.mapView.onCreate(dialog.onSaveInstanceState())
        b.mapView.onResume()
        b.mapView.getMapAsync {googleMap->

            val latLng = LatLng(
                userCurrentLocation!!.latitude,
                userCurrentLocation!!.longitude
            ) //your lat lng
            val marker: Marker = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.app_name))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )!!
            // Enable GPS marker in Map
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {

            }
            googleMap.isMyLocationEnabled = true
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.animateCamera(CameraUpdateFactory.zoomTo(15f), 1000, null)
            googleMap.setOnCameraMoveListener {
                val midLatLng: LatLng = googleMap.cameraPosition.target
                if (marker != null) {
                    marker.position = midLatLng
                    nowLocation = marker.position

                    //Update the value background thread to UI thread
                    Handler(Looper.myLooper()!!).postDelayed(Runnable {

                        val geocoder = Geocoder(this)
                        val addresses: List<Address>? = geocoder.getFromLocation(
                            nowLocation?.latitude ?: 0.0,
                            nowLocation?.longitude ?: 0.0,
                            1
                        )

                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "${addresses?.get(0)?.getAddressLine(0)}",
                                Toast.LENGTH_SHORT
                            ).show()

                            b.street.text = addresses?.get(0)?.getAddressLine(0)
                        }

                    },1000)


                }
            }

            googleMap.setOnMapClickListener(this)
            googleMap.setOnMarkerClickListener(this)
            googleMap.setOnMapLongClickListener(this)

        }
        dialog.setCancelable(false)
        b.saveLocation.setOnClickListener {
            if (nowLocation != null) {
                dialog.dismiss()

                val geocoder =Geocoder(this)
                val addresses: List<Address>? = geocoder.getFromLocation(nowLocation?.latitude ?:0.0, nowLocation?.longitude ?:0.0, 1)

                Toast.makeText(this, "${addresses?.get(0)?.getAddressLine(0)}", Toast.LENGTH_SHORT).show()

            }
        }
    }

    override fun onMapClick(latLng: LatLng) {
        val geocoder =Geocoder(this)
        val addresses: List<Address>? = geocoder.getFromLocation(nowLocation?.latitude ?:0.0, nowLocation?.longitude ?:0.0, 1)

        Toast.makeText(this, "${addresses?.get(0)?.getAddressLine(0)}", Toast.LENGTH_SHORT).show()
    }

    override fun onMarkerClick(p0: Marker): Boolean {
        val geocoder =Geocoder(this)
        val addresses: List<Address>? = geocoder.getFromLocation(nowLocation?.latitude ?:0.0, nowLocation?.longitude ?:0.0, 1)

        Toast.makeText(this, "Marker :: ${addresses?.get(0)?.getAddressLine(0)}", Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onMapLongClick(p0: LatLng) {
        val geocoder =Geocoder(this)
        val addresses: List<Address>? = geocoder.getFromLocation(nowLocation?.latitude ?:0.0, nowLocation?.longitude ?:0.0, 1)

        Toast.makeText(this, "LongClick:: ${addresses?.get(0)?.getAddressLine(0)}", Toast.LENGTH_SHORT).show()
    }



}

interface UpdateUiDelegate {
    fun onUpdateUi()
}
