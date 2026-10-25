package com.absensiapp.school.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

object SchoolLocationHelper {

    // SMKS Taruna Jaya Prawira Tuban coordinates
    // Jl. Dr. Wahidin Sudirohusodo, Kelurahan Sidorejo, Tuban, Jawa Timur
    const val SCHOOL_LATITUDE = -6.899605
    const val SCHOOL_LONGITUDE = 112.0501433
    const val MAX_ALLOWED_RADIUS_METERS = 250f // 250 meter radius
    const val DEFAULT_CLASS_RADIUS_METERS = 50f // 50 meter radius kelas

    fun calculateDistanceMeters(userLat: Double, userLng: Double): Float {
        return calculateDistanceMeters(userLat, userLng, SCHOOL_LATITUDE, SCHOOL_LONGITUDE)
    }

    fun calculateDistanceMeters(
        userLat: Double,
        userLng: Double,
        targetLat: Double,
        targetLng: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            userLat, userLng,
            targetLat, targetLng,
            results
        )
        return results[0]
    }

    fun isInsideSchoolZone(distanceMeters: Float): Boolean {
        return distanceMeters <= MAX_ALLOWED_RADIUS_METERS
    }

    fun isInsideClassZone(distanceMeters: Float, radiusMeters: Float = DEFAULT_CLASS_RADIUS_METERS): Boolean {
        return distanceMeters <= radiusMeters
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun requestSingleLocation(
        context: Context,
        onSuccess: (Location, Float) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onError("Izin lokasi GPS diperlukan untuk verifikasi zona sekolah.")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onError("Layanan lokasi tidak tersedia.")
            return
        }

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            onError("GPS perangkat belum diaktifkan. Mohon aktifkan GPS.")
            return
        }

        // Try last known location first for immediate response
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val bestLast = when {
            lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            else -> lastNet
        }

        if (bestLast != null) {
            val dist = calculateDistanceMeters(bestLast.latitude, bestLast.longitude)
            onSuccess(bestLast, dist)
            return
        }

        // Otherwise listen for single update
        val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                val dist = calculateDistanceMeters(location.latitude, location.longitude)
                onSuccess(location, dist)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            onError("Gagal mengambil koordinat lokasi: ${e.message}")
        }
    }
}
