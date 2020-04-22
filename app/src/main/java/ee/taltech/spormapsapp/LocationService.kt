package ee.taltech.spormapsapp

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.gms.location.*
import ee.taltech.spormapsapp.api.API.BASE_URL
import ee.taltech.spormapsapp.api.API.GPS_LOCATIONS
import ee.taltech.spormapsapp.api.API.GPS_SESSION
import ee.taltech.spormapsapp.helper.StateVariables.CP_average_speed
import ee.taltech.spormapsapp.helper.StateVariables.CP_distance_line
import ee.taltech.spormapsapp.helper.StateVariables.CP_distance_overall
import ee.taltech.spormapsapp.helper.StateVariables.WP_average_speed
import ee.taltech.spormapsapp.helper.StateVariables.WP_distance_line
import ee.taltech.spormapsapp.helper.StateVariables.WP_distance_overall
import ee.taltech.spormapsapp.helper.StateVariables.add_CP
import ee.taltech.spormapsapp.helper.StateVariables.add_WP
import ee.taltech.spormapsapp.helper.StateVariables.currentLocation
import ee.taltech.spormapsapp.helper.StateVariables.fillColumn
import ee.taltech.spormapsapp.helper.StateVariables.line_distance_covered
import ee.taltech.spormapsapp.helper.StateVariables.locationCP
import ee.taltech.spormapsapp.helper.StateVariables.locationStart
import ee.taltech.spormapsapp.helper.StateVariables.locationWP
import ee.taltech.spormapsapp.helper.StateVariables.oldLocation
import ee.taltech.spormapsapp.helper.StateVariables.overall_average_speed
import ee.taltech.spormapsapp.helper.StateVariables.overall_distance_covered
import ee.taltech.spormapsapp.helper.StateVariables.session_duration
import ee.taltech.spormapsapp.helper.StateVariables.state_code
import ee.taltech.spormapsapp.api.API
import ee.taltech.spormapsapp.api.WebApiSingletonHandler
import ee.taltech.spormapsapp.db.LocationCategory
import ee.taltech.spormapsapp.db.LocationCategoryParser
import ee.taltech.spormapsapp.db.LocationCategoryRepository
import ee.taltech.spormapsapp.helper.C
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*


class LocationService : Service() {

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName

        // The desired intervals for location updates. Inexact. Updates may be more or less frequent.
        const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000
        const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

        private var mInstance: LocationService? = null

        fun isServiceCreated(): Boolean {
            return try {
                // If instance was not cleared but the service was destroyed an Exception will be thrown
                mInstance != null && mInstance!!.ping()
            } catch (e: NullPointerException) {
                // destroyed/not-started
                false
            }
        }
    }

    private fun ping(): Boolean {
        return true
    }

    // DB
    private lateinit var databaseConnector: LocationCategoryRepository

    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()

    private val mLocationRequest: LocationRequest = LocationRequest()
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLocationCallback: LocationCallback? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())

    var token: String? = null

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        mInstance = this

        token = API.token

        databaseConnector = LocationCategoryRepository(this).open()

        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)
        broadcastReceiverIntentFilter.addAction(C.SESSION_END_TOKEN)

        registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        }

        getLastLocation()
        createLocationRequest()
        requestLocationUpdates()
        startRestTrackingSession()

    }

    private fun requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates")

        try {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback, Looper.myLooper()
            )
        } catch (unlikely: SecurityException) {
            Log.e(
                TAG,
                "Lost location permission. Could not request updates. $unlikely"
            )
        }
    }

    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")

        if (currentLocation == null) {
            locationStart = location
        } else {
            line_distance_covered = location.distanceTo(locationStart)
            overall_distance_covered += location.distanceTo(currentLocation)

            if (locationCP == null) {
                CP_distance_line = -1.0f;
            } else {
                CP_distance_line = location.distanceTo(locationCP)
                CP_distance_overall += location.distanceTo(currentLocation)
            }

            if (locationWP == null) {
                WP_distance_line = -1.0f;
            } else {
                WP_distance_line = location.distanceTo(locationWP)
                WP_distance_overall += location.distanceTo(currentLocation)
            }

        }
        // save the location for calculations
        if (currentLocation != null) {
            oldLocation = Location(currentLocation)
        }
        currentLocation = location

        showNotification()

        saveRestLocation(location, API.REST_LOCATION_ID_LOC)

        // broadcast new location to UI
        val intent = Intent(C.LOCATION_UPDATE_ACTION)
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LOCATION, location)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

    }

    private fun createLocationRequest() {
        mLocationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.maxWaitTime = UPDATE_INTERVAL_IN_MILLISECONDS
    }

    private fun getLastLocation() {
        try {
            mFusedLocationClient.lastLocation
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.w(TAG, "task successfull")
                        if (task.result != null) {
                            onNewLocation(task.result!!)
                        }
                    } else {

                        Log.w(TAG, "Failed to get location." + task.exception)
                    }
                }
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }
    }

    private fun startRestTrackingSession() {
        val handler = WebApiSingletonHandler.getInstance(applicationContext)
        val requestJsonParameters = JSONObject()
        requestJsonParameters.put("name", "envomp " + Date().toString())
        requestJsonParameters.put("description", "envomp " + Date().toString())
        requestJsonParameters.put("paceMin", 60)
        requestJsonParameters.put("paceMax", 18 * 60)


        val httpRequest = object : JsonObjectRequest(
            Method.POST,
            BASE_URL + GPS_SESSION,
            requestJsonParameters,
            Response.Listener { response ->
                Log.d(TAG, response.toString())
                state_code = response.getString("id")
            },
            Response.ErrorListener { error ->
                Log.d(TAG, error.toString())
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                for ((key, value) in super.getHeaders()) {
                    headers[key] = value
                }

                if (token == null) {
                    token = API.token
                }

                if (token != null) {
                    headers["Authorization"] = "Bearer " + token!!
                }

                return headers
            }
        }


        handler.addToRequestQueue(httpRequest)
    }

    fun saveRestLocation(location: Location, location_type: String) {
        if (token == null || state_code == null) {
            return
        }

        databaseConnector.addLocation(
            LocationCategory(
                location,
                "LOC",
                state_code!!
            )
        )

        val handler = WebApiSingletonHandler.getInstance(applicationContext)

        val requestJsonParameters = JSONObject()

        requestJsonParameters.put("recordedAt", dateFormat.format(Date(location.time)))
        requestJsonParameters.put("latitude", location.latitude)
        requestJsonParameters.put("longitude", location.longitude)
        requestJsonParameters.put("accuracy", location.accuracy)
        requestJsonParameters.put("altitude", location.altitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestJsonParameters.put("verticalAccuracy", location.verticalAccuracyMeters)
        }
        requestJsonParameters.put("gpsSessionId", state_code)
        requestJsonParameters.put("gpsLocationTypeId", location_type)


        val httpRequest = object : JsonObjectRequest(
            Method.POST,
            BASE_URL + GPS_LOCATIONS,
            requestJsonParameters,
            Response.Listener { response ->
                Log.d(TAG, response.toString())
            },
            Response.ErrorListener { error ->
                Log.d(TAG, error.toString())
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                for ((key, value) in super.getHeaders()) {
                    headers[key] = value
                }
                headers["Authorization"] = "Bearer " + token!!
                return headers
            }
        }
        handler.addToRequestQueue(httpRequest)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()

        //stop location updates
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)

        // close DB connection
        databaseConnector.close()

        // remove notifications
        NotificationManagerCompat.from(this).cancelAll()

        // don't forget to unregister brodcast receiver!!!!
        unregisterReceiver(broadcastReceiver)

        // broadcast stop to UI
        val intent = Intent(C.LOCATION_UPDATE_ACTION)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

    }

    override fun onLowMemory() {
        Log.d(TAG, "onLowMemory")
        super.onLowMemory()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        // set counters and locations to 0/null
        currentLocation = null
        locationStart = null
        locationCP = null
        locationWP = null

        line_distance_covered = 0f
        overall_distance_covered = 0f
        CP_distance_line = 0f
        CP_distance_overall = 0f
        WP_distance_line = 0f
        WP_distance_overall = 0f


        showNotification()

        return START_STICKY
        //return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        TODO("not implemented")
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)

    }

    fun showNotification() {
        val intentCp = Intent(C.NOTIFICATION_ACTION_CP)
        val intentWp = Intent(C.NOTIFICATION_ACTION_WP)

        val pendingIntentCp = PendingIntent.getBroadcast(this, 0, intentCp, 0)
        val pendingIntentWp = PendingIntent.getBroadcast(this, 0, intentWp, 0)

        val notifyview = RemoteViews(packageName, R.layout.track_control)

        notifyview.setOnClickPendingIntent(R.id.imageButtonCP, pendingIntentCp)
        notifyview.setOnClickPendingIntent(R.id.imageButtonWP, pendingIntentWp)

        overall_average_speed = fillColumn(
            notifyview,
            session_duration,
            overall_distance_covered,
            R.id.col4,
            String.format(
                "%s:%s:%s",
                (session_duration.toInt() / 3600).toString().padStart(2, '0'),
                ((session_duration.toInt() / 60) % 60).toString().padStart(2, '0'),
                (session_duration.toInt() % 60).toString().padStart(2, '0')
            )
        )

        CP_average_speed = fillColumn(
            notifyview,
            session_duration,
            CP_distance_overall,
            R.id.col5,
            "${((CP_distance_line * 10).toInt().toDouble() / 10)} m"
        )

        WP_average_speed = fillColumn(
            notifyview,
            session_duration,
            WP_distance_overall,
            R.id.col6,
            "${((WP_distance_line * 10).toInt().toDouble() / 10)} m"
        )

        // construct and show notification
        val builder = NotificationCompat.Builder(applicationContext, C.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        builder.setContent(notifyview)

        // Super important, start as foreground service - ie android considers this as an active app. Need visual reminder - notification.
        // must be called within 5 secs after service starts.
        startForeground(C.NOTIFICATION_ID, builder.build())

    }

    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, intent!!.action)
            when (intent.action) {
                C.NOTIFICATION_ACTION_WP -> {

                    locationWP = currentLocation
                    add_WP = true
                    WP_distance_line = 0f
                    WP_distance_overall = 0f

                    // broadcast new location to UI
                    val intent = Intent(C.LOCATION_UPDATE_ACTION_WP)
                    intent.putExtra(C.LOCATION_UPDATE_ACTION_WP, locationWP)
                    LocalBroadcastManager.getInstance(mInstance!!.applicationContext)
                        .sendBroadcast(intent)

                    saveRestLocation(locationWP!!, API.REST_LOCATION_ID_WP)
                    showNotification()

                }
                C.NOTIFICATION_ACTION_CP -> {

                    locationCP = currentLocation
                    add_CP = true
                    CP_distance_line = 0f
                    CP_distance_overall = 0f

                    // broadcast new location to UI
                    val intent = Intent(C.LOCATION_UPDATE_ACTION_CP)
                    intent.putExtra(C.LOCATION_UPDATE_ACTION_CP, locationCP)
                    LocalBroadcastManager.getInstance(mInstance!!.applicationContext)
                        .sendBroadcast(intent)

                    saveRestLocation(locationCP!!, API.REST_LOCATION_ID_CP)
                    showNotification()

                }

                C.SESSION_END_TOKEN -> {

                    token = intent.extras?.get(C.SESSION_END_TOKEN).toString()

                }
            }
        }
    }

}