package com.example.locationpicker

import android.location.Address
import com.google.android.gms.maps.model.LatLng

interface GeocoderDataSourceInterface {
    suspend fun autoCompleteFromLocationName(query: String): List<PlaceSuggestion>
    suspend fun getFromLocation(latitude: Double, longitude: Double): List<Address>?


}