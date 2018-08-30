package com.awareframework.android.sensor.locations

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationManager.*
import android.os.Bundle
import android.os.IBinder
import android.support.v4.content.ContextCompat
import android.util.Log
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.locations.model.LocationsData
import java.lang.Math.toRadians
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Location service for Aware framework
 * Provides mobile device network triangulation and GPS location
 *
 * @author  sercant
 * @date 30/08/2018
 */
class LocationsSensor : AwareSensor(), LocationListener {

    companion object {
        const val TAG = "AWARE::Locations"

        /**
         * Fired event: New location available
         */
        const val ACTION_AWARE_LOCATIONS = "ACTION_AWARE_LOCATIONS"

        /**
         * Fired event: GPS location is active
         */
        const val ACTION_AWARE_GPS_LOCATION_ENABLED = "ACTION_AWARE_GPS_LOCATION_ENABLED"

        /**
         * Fired event: Network location is active
         */
        const val ACTION_AWARE_NETWORK_LOCATION_ENABLED = "ACTION_AWARE_NETWORK_LOCATION_ENABLED"

        /**
         * Fired event: GPS location disabled
         */
        const val ACTION_AWARE_GPS_LOCATION_DISABLED = "ACTION_AWARE_GPS_LOCATION_DISABLED"

        /**
         * Fired event: Network location disabled
         */
        const val ACTION_AWARE_NETWORK_LOCATION_DISABLED = "ACTION_AWARE_NETWORK_LOCATION_DISABLED"

        const val ACTION_AWARE_LOCATION_START = "com.awareframework.android.sensor.locations.SENSOR_START"
        const val ACTION_AWARE_LOCATION_STOP = "com.awareframework.android.sensor.locations.SENSOR_STOP"

        const val ACTION_AWARE_LOCATION_SET_LABEL = "com.awareframework.android.sensor.locations.SET_LABEL"
        const val EXTRA_LABEL = "label"

        const val ACTION_AWARE_LOCATION_SYNC = "com.awareframework.android.sensor.locations.SENSOR_SYNC"

        val CONFIG = Config()
        val REQUIRED_PERMISSIONS = arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION)

        fun start(context: Context, config: Config? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, LocationsSensor::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationsSensor::class.java))
        }

        private var FREQUENCY_NETWORK = -1
        private var FREQUENCY_GPS = -1
        private var FREQUENCY_PASSIVE = -1
    }

    private var locationManager: LocationManager? = null

    /**
     * This listener will keep track for failed GPS location requests
     * TODO: extend to log satellite information
     */
    @SuppressLint("MissingPermission")
    private val gpsStatusListener = GpsStatus.Listener { event ->
        when (event) {
            GpsStatus.GPS_EVENT_FIRST_FIX -> {

            }

            GpsStatus.GPS_EVENT_SATELLITE_STATUS -> {

            }

            GpsStatus.GPS_EVENT_STARTED -> {

            }

            GpsStatus.GPS_EVENT_STOPPED -> {
                if (hasPermissions()) {
                    //Save best location, could be GPS or network
                    //This covers the case when the GPS stopped and we did not get a location fix.
                    val lastGPS = locationManager?.getLastKnownLocation(GPS_PROVIDER)

                    val lastNetwork: Location? = if (locationManager?.getProvider(NETWORK_PROVIDER) != null) {
                        locationManager?.requestSingleUpdate(NETWORK_PROVIDER, this, mainLooper)
                        locationManager?.getLastKnownLocation(NETWORK_PROVIDER)
                    } else null

                    val bestLocation =
                            if (isBetterLocation(lastNetwork, lastGPS))
                                lastNetwork
                            else
                                lastGPS

                    val permitted =
                            if (bestLocation != null)
                                testGeoFence(bestLocation.latitude, bestLocation.longitude)
                            else true

                    logd("Locations geofencing: permitted=$permitted")

                    if (bestLocation != null) {
                        val data = LocationsData().apply {
                            timestamp = System.currentTimeMillis()
                            deviceId = CONFIG.deviceId
                            provider = bestLocation.provider
                            label = CONFIG.label

                            if (permitted) {
                                latitude = bestLocation.latitude
                                longitude = bestLocation.longitude
                                bearing = bestLocation.bearing
                                speed = bestLocation.speed
                                altitude = bestLocation.altitude
                                accuracy = bestLocation.accuracy
                            } else {
                                label = "outOfBounds"
                            }
                        }

                        dbEngine?.save(data, LocationsData.TABLE_NAME)
                        sendBroadcast(Intent(ACTION_AWARE_LOCATIONS))
                    }
                }
            }
        }
    }

    private val locationsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return

            when (intent.action) {
                ACTION_AWARE_LOCATION_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_LOCATION_SYNC -> onSync(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        initializeDbEngine(CONFIG)

        registerReceiver(locationsReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_LOCATION_SET_LABEL)
            addAction(ACTION_AWARE_LOCATION_SYNC)
        })

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        logd("Locations service created!")
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (!hasPermissions()) {
            logw("Missing permissions detected.")
            return START_NOT_STICKY
        }

        if (CONFIG.statusGps) {
            if (locationManager?.getProvider(GPS_PROVIDER) != null) {
                if (FREQUENCY_GPS != CONFIG.frequencyGps) {
                    locationManager?.requestLocationUpdates(
                            GPS_PROVIDER,
                            (CONFIG.frequencyGps * 1000).toLong(),
                            CONFIG.minGpsAccuracy.toFloat(),
                            this
                    )
                    locationManager?.removeGpsStatusListener(gpsStatusListener)
                    locationManager?.addGpsStatusListener(gpsStatusListener)

                    FREQUENCY_GPS = CONFIG.frequencyGps
                }
                logd("Location tracking with GPS is active: $FREQUENCY_GPS s")
            } else {
                val data = LocationsData().apply {
                    timestamp = System.currentTimeMillis()
                    deviceId = CONFIG.deviceId
                    label = "disabled"
                    provider = GPS_PROVIDER
                }

                dbEngine?.save(data, LocationsData.TABLE_NAME)
                logd("Location tracking with GPS is not available")
            }
        }

        if (CONFIG.statusNetwork) {
            if (locationManager?.getProvider(NETWORK_PROVIDER) != null) {
                if (FREQUENCY_NETWORK != CONFIG.frequencyNetwork) {
                    locationManager?.requestLocationUpdates(
                            NETWORK_PROVIDER,
                            (CONFIG.frequencyNetwork * 1000).toLong(),
                            CONFIG.minNetworkAccuracy.toFloat(),
                            this
                    )

                    FREQUENCY_NETWORK = CONFIG.frequencyNetwork
                }

                logd("Location tracking with Network is active: $FREQUENCY_NETWORK s")
            } else {
                val data = LocationsData().apply {
                    timestamp = System.currentTimeMillis()
                    deviceId = CONFIG.deviceId
                    label = "disabled"
                    provider = NETWORK_PROVIDER
                }

                dbEngine?.save(data, LocationsData.TABLE_NAME)
                logd("Location tracking with Network is not available")
            }
        }

        if (CONFIG.statusPassive) {
            if (locationManager?.getProvider(PASSIVE_PROVIDER) != null) {
                // We treat this provider differently.  Since there is no battery use
                // and we don't have actual control over frequency, we register for
                // frequency=60s and no movement threshold.
                val staticFrequencyPassive = 60 * 1000
                if (FREQUENCY_PASSIVE != staticFrequencyPassive) {
                    locationManager?.requestLocationUpdates(
                            PASSIVE_PROVIDER,
                            staticFrequencyPassive.toLong(),
                            0f,
                            this
                    )

                    FREQUENCY_PASSIVE = staticFrequencyPassive
                }

                logd("Location tracking with passive provider is active: $FREQUENCY_PASSIVE")
            } else {
                val data = LocationsData().apply {
                    timestamp = System.currentTimeMillis()
                    deviceId = CONFIG.deviceId
                    label = "disabled"
                    provider = PASSIVE_PROVIDER
                }

                dbEngine?.save(data, LocationsData.TABLE_NAME)
                logd("Location tracking with passive provider is not available")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        dbEngine?.close()

        unregisterReceiver(locationsReceiver)

        if (hasPermissions()) {
            locationManager?.removeUpdates(this)
            locationManager?.removeGpsStatusListener(gpsStatusListener)
        }

        logd("Locations service terminated.")
    }

    private fun hasPermissions(): Boolean = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    override fun onLocationChanged(newLocation: Location?) {
        newLocation ?: return

        logd("onLocationChange: provider=${newLocation.provider} location=$newLocation")

        if (CONFIG.saveAll) {
            saveLocation(newLocation)
            sendBroadcast(Intent(ACTION_AWARE_LOCATIONS))
            return
        }

        val bestLocation = if (CONFIG.statusGps && CONFIG.statusNetwork && hasPermissions()) {
            val lastGPS = locationManager?.getLastKnownLocation(GPS_PROVIDER)
            val lastNetwork = locationManager?.getLastKnownLocation(NETWORK_PROVIDER)

            if (isBetterLocation(lastNetwork, lastGPS)) {
                if (isBetterLocation(newLocation, lastNetwork))
                    newLocation
                else
                    lastNetwork
            } else {
                if (isBetterLocation(newLocation, lastGPS))
                    newLocation
                else
                    lastGPS
            }
        } else newLocation

        saveLocation(bestLocation)
        sendBroadcast(Intent(ACTION_AWARE_LOCATIONS))
    }

    @SuppressLint("MissingPermission")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        logd("onStatusChanged: $provider Status: $status Extras: $extras")

        // Save ALL locations, no matter which provider it comes from or how it relates to past
        // locations.
        if (CONFIG.saveAll) {
            var updated = false

            locationManager?.getLastKnownLocation(GPS_PROVIDER)?.let {
                saveLocation(it)
                updated = true
            }

            locationManager?.getLastKnownLocation(NETWORK_PROVIDER)?.let {
                saveLocation(it)
                updated = true
            }

            if (updated) sendBroadcast(Intent(ACTION_AWARE_LOCATIONS))
            return
        }

        //Save best location, could be GPS or network
        //This covers the case when the GPS stopped and we did not get a location fix.
        val lastGPS = locationManager?.getLastKnownLocation(GPS_PROVIDER)

        val lastNetwork = if (locationManager?.getProvider(NETWORK_PROVIDER) != null) {
            locationManager?.requestSingleUpdate(NETWORK_PROVIDER, this, mainLooper)
            locationManager?.getLastKnownLocation(NETWORK_PROVIDER)
        } else null


        val bestLocation = if (isBetterLocation(lastNetwork, lastGPS)) {
            lastNetwork
        } else {
            lastGPS
        }

        saveLocation(bestLocation)
        sendBroadcast(Intent(ACTION_AWARE_LOCATIONS))
    }

    override fun onProviderEnabled(provider: String?) {
        logd("onProviderEnabled: $provider")

        if (provider == GPS_PROVIDER) {
            saveProviderChange(GPS_PROVIDER, "enabled")

            logd(ACTION_AWARE_GPS_LOCATION_ENABLED)
            sendBroadcast(Intent(ACTION_AWARE_GPS_LOCATION_ENABLED))
        } else if (provider == NETWORK_PROVIDER) {
            saveProviderChange(NETWORK_PROVIDER, "enabled")

            logd(ACTION_AWARE_NETWORK_LOCATION_ENABLED)
            sendBroadcast(Intent(ACTION_AWARE_NETWORK_LOCATION_ENABLED))
        }
    }

    override fun onProviderDisabled(provider: String?) {
        logd("onProviderDisabled: $provider")

        if (provider == GPS_PROVIDER) {
            saveProviderChange(GPS_PROVIDER, "disabled")

            logd(ACTION_AWARE_GPS_LOCATION_DISABLED)
            sendBroadcast(Intent(ACTION_AWARE_GPS_LOCATION_DISABLED))
        } else if (provider == NETWORK_PROVIDER) {
            saveProviderChange(NETWORK_PROVIDER, "disabled")

            logd(ACTION_AWARE_NETWORK_LOCATION_DISABLED)
            sendBroadcast(Intent(ACTION_AWARE_NETWORK_LOCATION_DISABLED))
        }
    }

    private fun saveProviderChange(provider: String, label: String): LocationsData {
        val data = LocationsData().apply {
            timestamp = System.currentTimeMillis()
            deviceId = CONFIG.deviceId

            this@apply.label = label
            this@apply.provider = provider
        }

        dbEngine?.save(data, LocationsData.TABLE_NAME)

        return data
    }

    private fun testGeoFence(lat0: Double, lon0: Double): Boolean {
        // Find fence and if we even need to fence.
        logd("Location geofence: testing against config=${CONFIG.geoFences}")

        // If no value, then always accept locations
        val geoFences = CONFIG.geoFences ?: return true
        if (geoFences == "null") return true

        // Separate geofence string by spaces, tabs, and semicolon
        val fences = geoFences.split("[ \t;]+")
        // Test each part separately, if any part is true, return true.
        fences.forEachIndexed { i, fence ->
            val parts = fence.split(",")
            // Circular fences.  Distance in METERS.
            if (parts.size == 3) {
                if (wgs84Distance(lat0, lon0, parts[0].toDouble(), parts[1].toDouble()) < parts[2].toDouble()) {
                    logd("Location geofence: within $fence")
                    return true
                }
            }

            // Rectangular fence
            if (parts[0] == "rect" && parts.size == 5) {
                val lat1 = parts[1].toDouble()
                val lon1 = parts[2].toDouble()
                val lat2 = parts[3].toDouble()
                val lon2 = parts[4].toDouble()

                // Be safe in case order of xxx1 and xxx2 are reversed,
                // so test twice.  Is there a better way to do this?
                if (((lat1 < lat0 && lat0 < lat2)
                                || (lat2 < lat0 && lat0 < lat1))
                        && ((lon1 < lon0 && lon0 < lon2)
                                || (lon2 < lon0 && lon0 < lon1))
                ) {
                    logd("Location geofence: within $fence")
                    return true
                }
            }
        }

        logd("Location geofence: not in any fences")
        return false
    }

    /**
     * Haversine formula for geographic distances.  Returns distance in meters.
     */
    private fun wgs84Distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6378137.0

        val dLat = toRadians(lat2 - lat1)
        val dLon = toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(toRadians(lat1)) * cos(toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun isBetterLocation(newLocation: Location?, lastLocation: Location?): Boolean {
        newLocation ?: return false
        lastLocation ?: return true

        val timeDelta = newLocation.time - lastLocation.time
        val isSignificantlyNewer = timeDelta > 1000 * CONFIG.expirationTime
        val isSignificantlyOlder = timeDelta < -(1000 * CONFIG.expirationTime)
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) return true
        else if (isSignificantlyOlder) return false

        val accuracyDelta = newLocation.accuracy - lastLocation.accuracy
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 200
        val isFromSameProvider = newLocation.provider == lastLocation.provider

        if (isMoreAccurate
                || (isNewer && !isLessAccurate)
                || (isNewer && !isSignificantlyLessAccurate && isFromSameProvider))
            return true

        return false
    }

    /**
     * Save a location, handling geofencing.
     *
     * @param bestLocation Location to save
     */
    private fun saveLocation(bestLocation: Location?) {
        bestLocation ?: return  //no location available

        // Are we within the geofence, if we are given one?
        val permitted = testGeoFence(bestLocation.latitude, bestLocation.longitude)
        logd("geofencing: permitted=$permitted")

        val data = LocationsData().apply {
            timestamp = System.currentTimeMillis()
            deviceId = CONFIG.deviceId
            label = CONFIG.label

            provider = bestLocation.provider

            if (permitted) {
                latitude = bestLocation.latitude
                longitude = bestLocation.longitude
                bearing = bestLocation.bearing
                speed = bestLocation.bearing
                altitude = bestLocation.altitude
                accuracy = bestLocation.accuracy
            } else {
                label = "outOfBounds"
            }
        }

        dbEngine?.save(data, LocationsData.TABLE_NAME)

        CONFIG.sensorObserver?.onLocationChanged(data)
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(LocationsData.TABLE_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class Config(
            var sensorObserver: Observer? = null,
            var geoFences: String? = null,
            var statusGps: Boolean = true,
            var statusNetwork: Boolean = true,
            var statusPassive: Boolean = true,
            var frequencyGps: Int = 180,
            var minGpsAccuracy: Int = 150,
            var frequencyNetwork: Int = 300,
            var minNetworkAccuracy: Int = 1500,
            var expirationTime: Long = 300,
            var saveAll: Boolean = false
    ) : SensorConfig(dbPath = "aware_locations") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is Config) {
                sensorObserver = config.sensorObserver
                geoFences = config.geoFences
                statusGps = config.statusGps
                statusNetwork = config.statusNetwork
                statusPassive = config.statusPassive
                frequencyGps = config.frequencyGps
                minGpsAccuracy = config.minGpsAccuracy
                frequencyNetwork = config.frequencyNetwork
                minNetworkAccuracy = config.minNetworkAccuracy
                expirationTime = config.expirationTime
                saveAll = config.saveAll
            }
        }
    }

    interface Observer {
        fun onLocationChanged(data: LocationsData)
    }

    class LocationsSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        start(context)
                    }
                }

                ACTION_AWARE_LOCATION_STOP,
                SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stop(context)
                }

                ACTION_AWARE_LOCATION_START -> {
                    start(context)
                }
            }
        }
    }

    override fun sendBroadcast(intent: Intent?) {
        intent?.let {
            logd(it.action)
        }

        super.sendBroadcast(intent)
    }
}

private fun logd(text: String) {
    if (LocationsSensor.CONFIG.debug) Log.d(LocationsSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(LocationsSensor.TAG, text)
}