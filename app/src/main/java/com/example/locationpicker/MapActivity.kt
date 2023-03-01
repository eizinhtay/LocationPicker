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
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
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
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import java.io.IOException
import java.util.*


private const val MIN_CHARACTERS = 2

private const val DEFAULT_ZOOM = 16
private const val WIDER_ZOOM = 6
private var hasWiderZoom = false

class MapActivity : AppCompatActivity(), GeocoderViewInterface, OnMapReadyCallback,
    android.location.LocationListener {
    private val PERMISSION_ID = 1
    private val REQUEST_CHECK_SETTINGS = 100
    private var nowLocation: LatLng? = null
    private var adapter: ArrayAdapter<String>? = null
    private var placeResolution = false

    var userCurrentLocation: Location? = null
    private lateinit var client: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private var geocoderPresenter: GeocoderPresenter? = null
    private var googleGeocoderDataSource: GoogleGeocoderDataSource? = null
    private lateinit var b: LocationDialogBinding
    private lateinit var binding: ActivityMapBinding
    private var currentMarker: Marker? = null
    private var map: GoogleMap? = null
    private var currentLocation: Location? = null

    private val defaultZoom: Int
        get() {
            return if (hasWiderZoom) {
                WIDER_ZOOM
            } else {
                DEFAULT_ZOOM
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        b =
            LocationDialogBinding.inflate(LayoutInflater.from(this@MapActivity))

        setContentView(binding.root)
        Places.initialize(applicationContext, getString(R.string.Api_key))
        placesClient = Places.createClient(this)
        client = LocationServices.getFusedLocationProviderClient(this)

        binding.pickLocation.setOnClickListener {
            getLastLocation()
        }
        checkLocationPermission()
        googleGeocoderDataSource = GoogleGeocoderDataSource(
            NetworkClient(),
            SuggestionBuilder(),
            AddressBuilder(),

            )
        val geocoderRepository = GeocoderRepository(
            googleGeocoderDataSource
        )
        geocoderPresenter = GeocoderPresenter(
            LocationProvider(applicationContext),
            geocoderRepository
        )
        geocoderPresenter!!.setUI(this)
        //needToCheckNetworkConnection
        if (currentLocation == null) {
            geocoderPresenter?.getLastKnownLocation()
        }

    }

    private fun checkLocationPermission() {
        if (
            PermissionUtils.shouldRequestLocationStoragePermission(applicationContext)
        ) {
            PermissionUtils.requestLocationPermission(this)
        }
    }

    /**
     * Method to check for permissions

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
     */
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
        if (PermissionUtils.isLocationPermissionGranted(this)) {
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

    @SuppressLint("MissingPermission")
    private fun startLocationService() {
        b =
            LocationDialogBinding.inflate(LayoutInflater.from(this@MapActivity))
        val dialog = Dialog(this@MapActivity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(b.root)
        dialog.show()
        MapsInitializer.initialize(this@MapActivity)
        b.mapView.onCreate(dialog.onSaveInstanceState())
        b.mapView.onResume()
//        b.mapView.getMapAsync { googleMap ->
//
//            val latLng = LatLng(
//                userCurrentLocation!!.latitude,
//                userCurrentLocation!!.longitude
//            ) //your lat lng
//            val marker: Marker = googleMap.addMarker(
//                MarkerOptions()
//                    .position(latLng)
//                    .title(getString(R.string.app_name))
//                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
//            )!!
//            // Enable GPS marker in Map
//
//            googleMap.isMyLocationEnabled = true
//            googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
//            googleMap.uiSettings.isZoomControlsEnabled = true
//            googleMap.animateCamera(CameraUpdateFactory.zoomTo(15f), 1000, null)
//            googleMap.setOnCameraMoveListener {
//                val midLatLng: LatLng = googleMap.cameraPosition.target
//                if (marker != null) {
//                    marker.position = midLatLng
//                    nowLocation = marker.position
//
//                    //Update the value background thread to UI thread
//                    Handler(Looper.myLooper()!!).postDelayed(Runnable {
//
////                        val geocoder = Geocoder(this)
////                        val addresses: List<Address>? = geocoder.getFromLocation(
////                            nowLocation?.latitude ?: 0.0,
////                            nowLocation?.longitude ?: 0.0,
////                            1
////                        )
////
////                        runOnUiThread {
////
////                            b.street.text = addresses?.get(0)?.getAddressLine(0)
////                        }
//
//                        showUi()
//
//                    }, 1000)
//
//
//                }
//            }
//
//
//        }
        b.mapView.getMapAsync(this)
        dialog.setCancelable(false)
        b.saveLocation.setOnClickListener {
            if (nowLocation != null) {
                dialog.dismiss()

                val geocoder = Geocoder(this)
                val addresses: List<Address>? = geocoder.getFromLocation(
                    nowLocation?.latitude ?: 0.0,
                    nowLocation?.longitude ?: 0.0,
                    1
                )

                Toast.makeText(this, "${addresses?.get(0)?.getAddressLine(0)}", Toast.LENGTH_SHORT)
                    .show()

            }
        }


        b.searchView?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                onSearchTextChanged(s.toString())
            }
        })
    }

    private fun showUi() {
        val geocoder = Geocoder(this)


        val name = nowLocation?.let { geocoderPresenter?.getInfoFromLocation(it) }
        //Toast.makeText(this, "name${addresses?.get(0)?.getAddressLine(0)}", Toast.LENGTH_SHORT).show()

        //Fetch address from location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(
                nowLocation?.latitude ?: 0.0,
                nowLocation?.longitude ?: 0.0,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        b.street.text = addresses?.get(0)?.getAddressLine(0)

                        // code
                    }

                    override fun onError(errorMessage: String?) {
                        super.onError(errorMessage)

                    }

                })
        } else {
            try {
                val addresses: List<Address>? = geocoder.getFromLocation(
                    nowLocation?.latitude ?: 0.0,
                    nowLocation?.longitude ?: 0.0,
                    1
                )
                val bestMatch = if (addresses?.isEmpty() == true) null else addresses?.get(0)
                b.street.text = bestMatch?.getAddressLine(0)
            } catch (e: IOException) {

            }
        }

    }

    private fun onSearchTextChanged(query: String) {
        if (query.isEmpty()) {

        } else {
            if (query.length > MIN_CHARACTERS) {
                retrieveLocationWithDebounceTimeFrom(query)
            }

        }
    }

    private fun retrieveLocationWithDebounceTimeFrom(term: Any) {

        val name = geocoderPresenter?.getSuggestionsFromLocationName(term.toString())
        Toast.makeText(this, "name::$name", Toast.LENGTH_SHORT).show()

    }

    private fun closeKeyboard() {
        val view = this.currentFocus
        view?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun willLoadLocation() {
        TODO("Not yet implemented")
    }

    override fun showLocations(addresses: List<Address>) {
        TODO("Not yet implemented")
    }

    override fun showSuggestions(suggestions: List<PlaceSuggestion>) {
        TODO("Not yet implemented")
    }

    override fun setAddressFromSuggestion(address: Address) {
        TODO("Not yet implemented")
    }

    override fun showDebouncedLocations(addresses: List<Address>) {
        TODO("Not yet implemented")
    }

    override fun didLoadLocation() {
        TODO("Not yet implemented")
    }

    override fun showLoadLocationError() {
        TODO("Not yet implemented")
    }

    override fun showLastLocation(location: Location) {
        currentLocation = location
        didGetLastLocation()
    }

    override fun didGetLastLocation() {
        if (currentLocation != null) {
            if (!Geocoder.isPresent()) {
                Toast.makeText(this, R.string.app_name, Toast.LENGTH_LONG).show()
                return
            }
            //setUpMapIfNeeded()
        }
        setUpDefaultMapLocation()
    }

    private fun setUpDefaultMapLocation() {
        if (currentLocation != null) {
            setCurrentPositionLocation()
        } else {
//            searchView = findViewById(R.id.leku_search)
//            retrieveLocationFrom(Locale.getDefault().displayCountry)
            hasWiderZoom = true
        }
    }

    override fun showLocationInfo(address: Pair<Address?, TimeZone?>) {
        TODO("Not yet implemented")
    }

    override fun willGetLocationInfo(latLng: LatLng) {
        Toast.makeText(this, "willGetLocationInfo", Toast.LENGTH_SHORT).show()

    }

    override fun didGetLocationInfo() {
        Toast.makeText(this, "Did Get LocationInfo", Toast.LENGTH_SHORT).show()
    }

    override fun showGetLocationInfoError() {
        TODO("Not yet implemented")
    }

    private fun setNewMapMarker(latLng: LatLng) {
        if (map != null) {
            currentMarker?.remove()
            val cameraPosition = CameraPosition.Builder().target(latLng)
                .zoom(defaultZoom.toFloat())
                .build()
            hasWiderZoom = false
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            currentMarker = addMarker(latLng)
            map?.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {
                }

                override fun onMarkerDrag(marker: Marker) {}

                override fun onMarkerDragEnd(marker: Marker) {
                    if (currentLocation == null) {
                        currentLocation = Location(getString(R.string.app_name))
                    }
                    // currentLekuPoi = null
                    currentLocation?.longitude = marker.position.longitude
                    currentLocation?.latitude = marker.position.latitude
                    setCurrentPositionLocation()
                }
            })
        }
    }

    private fun addMarker(latLng: LatLng): Marker? {
        map?.let {
            return it.addMarker(MarkerOptions().position(latLng).draggable(true))
        }
        return null
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setCurrentPositionLocation()

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
        googleMap.setOnCameraMoveListener {

            val midLatLng: LatLng = googleMap.cameraPosition.target
            if (marker != null) {
                marker.position = midLatLng
                nowLocation = marker.position
                setNewPosition(nowLocation!!)

            }
        }


    }

    private fun setNewPosition(nowLocation: LatLng) {
        if (currentLocation == null) {
            currentLocation = Location(getString(R.string.app_name))
        }
        currentLocation?.latitude = nowLocation.latitude
        currentLocation?.longitude = nowLocation.longitude
        setCurrentPositionLocation()
    }

    private fun setCurrentPositionLocation() {
        currentLocation?.let {
            setNewMapMarker(LatLng(it.latitude, it.longitude))
            geocoderPresenter?.getInfoFromLocation(
                LatLng(
                    it.latitude,
                    it.longitude
                )
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
    }

    private fun addPoiMarker(latLng: LatLng, title: String, address: String): Marker? {
        map?.let {
            return it.addMarker(
                MarkerOptions().position(latLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    .title(title)
                    .snippet(address)
            )
        }
        return null
    }

}

interface UpdateUiDelegate {
    fun onUpdateUi()
}
