package com.example.locationpicker

import android.location.Address
import com.google.android.gms.maps.model.LatLng

class GeocoderRepository(
    private val customGeocoder: GeocoderDataSourceInterface?,

){
    private val dataSources get() = listOf(customGeocoder)

    suspend fun autoCompleteFromLocationName(query: String): List<PlaceSuggestion> {
        dataSources.forEach {
            val data = it?.autoCompleteFromLocationName(query) ?: emptyList()
            if (data.isNotEmpty()) {
                return data
            }
        }
        return emptyList()
    }
    suspend fun getFromLocation(latLng: LatLng): List<Address> {
        dataSources.forEach {
            val data = it?.getFromLocation(latLng.latitude, latLng.longitude) ?: emptyList()
            if (data.isNotEmpty()) {
                return data
            }
        }
        return emptyList()
    }
}