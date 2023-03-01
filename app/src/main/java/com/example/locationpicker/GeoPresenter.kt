package com.example.locationpicker

import android.annotation.SuppressLint
import android.location.Address
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

class GeoPresenter {
}
class GeocoderPresenter @JvmOverloads constructor(
    private val locationProvider: LocationProvider,
    private val geocoderRepository: GeocoderRepository,

) {
    private var view: GeocoderViewInterface? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isGooglePlacesEnabled = false
    fun setUI(geocoderViewInterface: GeocoderViewInterface) {
        this.view = geocoderViewInterface
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation() {
        coroutineScope.launch(Dispatchers.IO) {
            val location = locationProvider.getLastKnownLocation()
            withContext(Dispatchers.Main) {
                location?.let {
                    view?.showLastLocation(it)
                }
                view?.didGetLastLocation()
            }
        }
    }

    fun getSuggestionsFromLocationName(query: String) {
        view?.willLoadLocation()
        coroutineScope.launch(Dispatchers.IO) {
            val suggestions = geocoderRepository.autoCompleteFromLocationName(query)
            withContext(Dispatchers.Main) {
                view?.showSuggestions(suggestions)
                view?.didLoadLocation()
            }
        }
    }
    fun getInfoFromLocation(latLng: LatLng) {
        view?.willGetLocationInfo(latLng)
        coroutineScope.launch(Dispatchers.IO) {
            val addresses = geocoderRepository.getFromLocation(latLng)
            if (addresses.isEmpty()) return@launch
            //val timeZone = returnTimeZone(addresses.first())
            withContext(Dispatchers.Main) {
               // view?.showLocationInfo(timeZone)
                view?.didGetLocationInfo()
            }
        }
    }
//    private fun returnTimeZone(address: Address?): Pair<Address?, TimeZone?> {
//        address?.let {
//            return Pair(it, googleTimeZoneDataSource?.getTimeZone(it.latitude, it.longitude))
//        }
//        return Pair(null, null)
//    }


    private fun getMergedList(
        geocoderList: List<Address>,
        placesList: List<Address>
    ): List<Address> {
        val mergedList = ArrayList<Address>()
        mergedList.addAll(geocoderList)
        mergedList.addAll(placesList)
        return mergedList
    }
}
