package com.refsix.wear.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

class GpsTracker(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun startTracking(onLocation: (Location) -> Unit, onFixChanged: (Boolean) -> Unit) {
        if (listener != null) return

        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onFixChanged(true)
                onLocation(location)
            }
            override fun onProviderEnabled(provider: String)  { onFixChanged(true) }
            override fun onProviderDisabled(provider: String) { onFixChanged(false) }
        }
        listener = l

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider != null) {
            locationManager.requestLocationUpdates(
                provider,
                10_000L,   // min 10 s between updates
                2f,        // min 2 m displacement
                l,
                Looper.getMainLooper()
            )
        }
    }

    fun stopTracking() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }
}
