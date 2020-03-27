package ee.taltech.spormapsapp

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlin.math.roundToInt


class LocationService : Service() {

    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    // The desired intervals for location updates. Inexact. Updates may be more or less frequent.
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 2000
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()

    private val mLocationRequest: LocationRequest = LocationRequest()
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mLocationCallback: LocationCallback? = null

    // headers
    private val headerValues : StateVariables = StateVariables

    // last received location
    private var currentLocation: Location? = null

    // other locations
    private var locationStart: Location? = null
    private var locationCP: Location? = null
    private var locationWP: Location? = null



    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_CP)
        broadcastReceiverIntentFilter.addAction(C.NOTIFICATION_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)


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

    }

    fun requestLocationUpdates() {
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
        if (currentLocation == null){
            locationStart = location
            locationCP = location
            locationWP = location
        } else {
            headerValues.line_distance_covered = location.distanceTo(locationStart)
            headerValues.overall_distance_covered += location.distanceTo(currentLocation)

            headerValues.CP_distance_line = location.distanceTo(locationCP)
            headerValues.CP_distance_overall += location.distanceTo(currentLocation)

            headerValues.WP_distance_line = location.distanceTo(locationWP)
            headerValues.WP_distance_overall += location.distanceTo(currentLocation)
        }
        // save the location for calculations
        currentLocation = location

        showNotification()

        // broadcast new location to UI
        val intent = Intent(C.LOCATION_UPDATE_ACTION)
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, location.latitude)
        intent.putExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, location.longitude)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

    }

    private fun createLocationRequest() {
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        mLocationRequest.setMaxWaitTime(UPDATE_INTERVAL_IN_MILLISECONDS)
    }


    private fun getLastLocation() {
        try {
            mFusedLocationClient.lastLocation
                .addOnCompleteListener { task -> if (task.isSuccessful) {
                    Log.w(TAG, "task successfull");
                    if (task.result != null){
                        onNewLocation(task.result!!)
                    }
                } else {

                    Log.w(TAG, "Failed to get location." + task.exception)
                }}
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()

        //stop location updates
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)

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

        headerValues.line_distance_covered = 0f
        headerValues.overall_distance_covered = 0f
        headerValues.CP_distance_line = 0f
        headerValues.CP_distance_overall = 0f
        headerValues.WP_distance_line = 0f
        headerValues.WP_distance_overall = 0f


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

        var notifyview = RemoteViews(packageName, R.layout.track_control)

        notifyview.setOnClickPendingIntent(R.id.imageButtonCP, pendingIntentCp)
        notifyview.setOnClickPendingIntent(R.id.imageButtonWP, pendingIntentWp)

        headerValues.overall_average_speed = fillColumn(
            notifyview,
            headerValues.session_duration,
            headerValues.overall_distance_covered,
            R.id.col4,
            String.format(
                "%s:%s:%s", (headerValues.session_duration.toInt() / 3600).toString().padStart(2, '0'),
                ((headerValues.session_duration.toInt() / 60) % 60).toString().padStart(2, '0'),
                (headerValues.session_duration.toInt() % 60).toString().padStart(2, '0')
            )
        )

        headerValues.CP_average_speed =fillColumn(
            notifyview,
            headerValues.session_duration,
            headerValues.CP_distance_overall,
            R.id.col5,
            ((headerValues.CP_distance_line * 10).toInt().toDouble() / 10).toString()
        )

        headerValues.WP_average_speed = fillColumn(
            notifyview,
            headerValues.session_duration,
            headerValues.WP_distance_overall,
            R.id.col6,
            ((headerValues.WP_distance_line * 10).toInt().toDouble() / 10).toString()
        )

        // construct and show notification
        var builder = NotificationCompat.Builder(applicationContext, C.NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        builder.setContent(notifyview)

        // Super important, start as foreground service - ie android considers this as an active app. Need visual reminder - notification.
        // must be called within 5 secs after service starts.
        startForeground(C.NOTIFICATION_ID, builder.build())

    }

    private fun fillColumn(
        notifyview: RemoteViews,
        sessionDuration: Float,
        overallDistanceCovered: Float,
        col: Int,
        row2: String
    ): Double {

        var duration = sessionDuration.toInt()
        var covered = overallDistanceCovered.toInt()
        var averageSpeed = 0.0
        if (covered != 0) {
            averageSpeed =
                (((duration * 1000.0) / (overallDistanceCovered * 60.0) * 10).roundToInt() / 10).toDouble()
        }

        notifyview.setTextViewText(
            col,
            String.format(
                "%s\n%s\n%s",
                covered,
                row2,
                averageSpeed
            )
        )

        return averageSpeed
    }


    private inner class InnerBroadcastReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, intent!!.action)
            when(intent!!.action){
                C.NOTIFICATION_ACTION_WP -> {
                    locationWP = currentLocation
                    headerValues.WP_distance_line = 0f
                    headerValues.WP_distance_overall = 0f
                    showNotification()
                }
                C.NOTIFICATION_ACTION_CP -> {
                    locationCP = currentLocation
                    headerValues.CP_distance_line = 0f
                    headerValues.CP_distance_overall = 0f
                    showNotification()
                }
            }
        }
    }
}