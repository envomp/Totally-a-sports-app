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
import ee.taltech.spormapsapp.db.LocationCategoryRepository
import ee.taltech.spormapsapp.helper.C
import ee.taltech.spormapsapp.helper.StateVariables
import ee.taltech.spormapsapp.helper.StateVariables.sync_interval
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random


class LocationService : Service() {

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName

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
        currentLocation = locationStart

        databaseConnector = LocationCategoryRepository(this).open()

        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)
        broadcastReceiverIntentFilter.addAction(C.SESSION_TOKEN)

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

        if (currentLocation != null && location.distanceTo(currentLocation) < 1.0f || (location.hasAccuracy() && location.accuracy > 100)) {
            return
        }

        Log.i(TAG, "New location: $location")

        if (currentLocation == null) {
            locationStart = location
        } else {
            line_distance_covered = location.distanceTo(locationStart)
            overall_distance_covered += location.distanceTo(currentLocation)

            if (locationCP == null) {
                CP_distance_line = -1.0f
            } else {
                CP_distance_line = location.distanceTo(locationCP)
                CP_distance_overall += location.distanceTo(currentLocation)
            }

            if (locationWP == null) {
                WP_distance_line = -1.0f
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
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LOCATION_OLD, oldLocation)
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LOCATION, currentLocation)
        PendingIntent.getBroadcast(mInstance, 0, intent, Random.nextInt(0, 1000)).send()

    }

    private fun createLocationRequest() {

        mLocationRequest.interval = sync_interval
        mLocationRequest.fastestInterval = sync_interval / 2
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.maxWaitTime = sync_interval
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

                saveRestLocation(locationStart!!, API.REST_LOCATION_ID_LOC)

                databaseConnector.addLocation(
                    LocationCategory(
                        locationStart!!,
                        "LOC",
                        state_code!!
                    )
                )

                val intent = Intent(C.SESSION_START_ACTION)
                intent.putExtra(C.DISPLAY_SESSION_HASH, state_code)
                PendingIntent.getBroadcast(this, 0, intent, Random.nextInt(0, 1000)).send()
            },
            Response.ErrorListener { _ ->
                // broadcast start to UI
                val intent = Intent(C.SESSION_START_FAILED)
                PendingIntent.getBroadcast(this, 0, intent, Random.nextInt(0, 1000)).send()
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

        handler.addToRequestQueue(httpRequest, false)
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


        val httpRequest = getJsonObjectRequest(requestJsonParameters)
        handler.addToRequestQueue(httpRequest, true)

    }

    private fun getJsonObjectRequest(
        requestJsonParameters: JSONObject
    ): JsonObjectRequest {
        return object : JsonObjectRequest(
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
        val intent = Intent(C.SESSION_END_ACTION)
        PendingIntent.getBroadcast(mInstance, 0, intent, Random.nextInt(0, 1000)).send()

        mInstance = null

    }

    override fun onLowMemory() {
        Log.d(TAG, "onLowMemory")
        super.onLowMemory()
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        StateVariables.hardReset()

        showNotification()

        return START_STICKY
        //return super.onStartCommand(intent, flags, startId)
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

                    locationWP = Location(currentLocation)

                    WP_distance_line = 0f
                    WP_distance_overall = 0f

                    // broadcast new location to UI
                    val intent = Intent(C.LOCATION_UPDATE_ACTION_WP)
                    intent.putExtra(C.LOCATION_UPDATE_ACTION_WP, locationWP)
                    PendingIntent.getBroadcast(mInstance, 0, intent, Random.nextInt(0, 1000)).send()

                    saveRestLocation(locationWP!!, API.REST_LOCATION_ID_WP)

                    databaseConnector.addLocation(
                        LocationCategory(
                            locationWP!!,
                            "WP",
                            state_code!!
                        )
                    )

                    showNotification()

                }
                C.NOTIFICATION_ACTION_CP -> {

                    locationCP = Location(currentLocation)

                    CP_distance_line = 0f
                    CP_distance_overall = 0f

                    // broadcast new location to UI
                    val intent = Intent(C.LOCATION_UPDATE_ACTION_CP)
                    intent.putExtra(C.LOCATION_UPDATE_ACTION_CP, locationCP)
                    PendingIntent.getBroadcast(mInstance, 0, intent, Random.nextInt(0, 1000)).send()

                    saveRestLocation(locationCP!!, API.REST_LOCATION_ID_CP)

                    databaseConnector.addLocation(
                        LocationCategory(
                            locationCP!!,
                            "CP",
                            state_code!!
                        )
                    )

                    showNotification()

                }

                C.SESSION_TOKEN -> {

                    token = intent.extras?.get(C.SESSION_TOKEN).toString()

                }
            }
        }
    }

}