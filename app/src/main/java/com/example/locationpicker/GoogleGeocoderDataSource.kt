package com.example.locationpicker

import android.location.Address
import com.google.android.gms.maps.model.LatLng
import org.json.JSONException
import java.util.*
private const val QUERY_AUTOCOMPLETE =
    "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=%1\$s&key=%2\$s"
private const val QUERY_LAT_LONG =
    "https://maps.googleapis.com/maps/api/geocode/json?latlng=%1\$f,%2\$f&key=%3\$s"
class GoogleGeocoderDataSource(
    private val networkClient: NetworkClient,
    private val suggestionBuilder: SuggestionBuilder,
    private val addressBuilder: AddressBuilder,


    ) : GeocoderDataSourceInterface {

    private var geolocationApiKey: String? = null

    fun setGeolocationApiKey(apiKey: String) {
        this.geolocationApiKey = apiKey
    }

    private var placesApiKey: String? = null
    fun setPlaceApiKey(apiKey: String) {
        this.placesApiKey = apiKey
    }

    override suspend fun autoCompleteFromLocationName(query: String): List<PlaceSuggestion> {
        val suggestions = mutableListOf<PlaceSuggestion>()
        if (placesApiKey == null) {
            return suggestions
        }
        return try {
            val result = networkClient.requestFromLocationName(
                String.format(Locale.ENGLISH, QUERY_AUTOCOMPLETE, query, placesApiKey)
            )
            if (result != null) {
                suggestions.addAll(suggestionBuilder.parseResult(result))
            }
            suggestions
        } catch (e: JSONException) {
            suggestions
        } catch (e: NetworkException) {
            suggestions
        }
    }

    override suspend fun getFromLocation(latitude: Double, longitude: Double): List<Address> {
        val addresses = mutableListOf<Address>()
        if (geolocationApiKey == null) {
            return addresses
        }
        return try {
            val result = networkClient.requestFromLocationName(
                String.format(Locale.ENGLISH, QUERY_LAT_LONG, latitude, longitude, geolocationApiKey)
            )
            if (result != null) {
                addresses.addAll(addressBuilder.parseArrayResult(result))
            }
            addresses
        } catch (e: JSONException) {
            addresses
        } catch (e: NetworkException) {
            addresses
        }
    }

}