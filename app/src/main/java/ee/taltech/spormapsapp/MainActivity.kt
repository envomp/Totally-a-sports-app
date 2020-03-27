package ee.taltech.spormapsapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.os.Handler
import android.view.animation.Animation.RELATIVE_TO_SELF
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.map.*
import java.lang.Math.toDegrees
import kotlin.math.round


class MainActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private var stateUID = 215761238

    private val headerValues: StateVariables = StateVariables

    // async
    private var locationServiceActive = false
    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()

    // sensors
    lateinit var sensorManager: SensorManager
    lateinit var image: ImageView
    lateinit var accelerometer: Sensor
    lateinit var magnetometer: Sensor

    // sensor data
    var currentDegree = 0.0f
    var lastAccelerometer = FloatArray(3)
    var lastMagnetometer = FloatArray(3)
    var lastAccelerometerSet = false
    var lastMagnetometerSet = false

    // Header
    private var userLatitude = 0.0;
    private var userLongitude = 0.0;
    private var direction = 0
    private val direction_map: HashMap<Int, String> =
        hashMapOf(0 to "CENTERED", 1 to "NORTH-UP", 2 to "DIRECTION-UP", 3 to "CHOSEN-UP")
    private var is_compass_toggeled = false
    private var is_options_toggeled = false

    // Dynamic modifiers
    private var hasPermissions = false
    private var started = false
    private var add_WP = false
    private var add_CP = false

    // map
    private lateinit var map: GoogleMap
    private var CP: Marker? = null // capture points__ Only one!
    private var WP: Marker? = null // way points__ Only one!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // safe to call every time
        createNotificationChannel()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            hasPermissions = true
        }

        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)

        with(mapView) {
            // Initialise the MapView
            onCreate(savedInstanceState)
            // Set the map ready callback to receive the GoogleMap object
            getMapAsync {
                map = it
                MapsInitializer.initialize(applicationContext)
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
                mapActions(it)

                // create bounds that encompass every location we reference

                addMarkersToMap()
            }
        }

        image = findViewById(R.id.imageViewCompass)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD)

        wireGameButtons()
        updateVisibleText()

    }

    private fun addMarkersToMap() {
        if (WP != null) {
            WP!!.remove()
            map.addMarker(
                MarkerOptions().position(WP!!.position)
                    .title("Added a way point")
                    .draggable(true)
                    .icon(vectorToBitmap(R.drawable.waypoint, 100, 150))
            )
        }

        if (CP != null) {
            CP!!.remove()
            map.addMarker(
                MarkerOptions().position(CP!!.position)
                    .title("Added a way point")
                    .draggable(true)
                    .icon(vectorToBitmap(R.drawable.capturepoint, 100, 150))
            )
        }


    }

    private fun mapActions(map: GoogleMap) {
        with(map) {
//            moveCamera(CameraUpdateFactory.newLatLngZoom(position, 13f))

            mapType = GoogleMap.MAP_TYPE_TERRAIN
            setOnMapClickListener {
                if (add_CP) {

                    CP?.remove()
                    CP = addMarker(
                        MarkerOptions().position(LatLng(userLatitude, userLongitude))
                            .title(String.format("Capture point"))
                            .draggable(true)
                            .icon(vectorToBitmap(R.drawable.capturepoint, 100, 150))
                    )

                    Toast.makeText(
                        this@MainActivity,
                        String.format("Added a capture point"),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    add_CP = false

                } else if (add_WP) {

                    WP?.remove()

                    WP = addMarker(
                        MarkerOptions().position(LatLng(it.latitude, it.longitude))
                            .title("Way point")
                            .draggable(true)
                            .icon(vectorToBitmap(R.drawable.waypoint, 100, 150))
                    )

                    Toast.makeText(this@MainActivity, "Added a way point", Toast.LENGTH_SHORT)
                        .show()
                    add_WP = false

                }

                setColorsAndTexts()
            }

            map.setOnMyLocationChangeListener { location ->

                when (direction) {
                    0 -> {
                        // case CENTERED
                        //                map.animateCamera(CameraUpdateFactory.newLatLngZoom(, 16f));
                        val center: CameraUpdate = CameraUpdateFactory.newLatLng(
                            LatLng(
                                location.latitude,
                                location.longitude
                            )
                        )
                        val zoom: CameraUpdate = CameraUpdateFactory.zoomTo(20f)
                        map.moveCamera(center)
                        map.animateCamera(zoom)
                    }
                    1 -> {
                        // case NORTH-UP
                    }
                    2 -> {
                        // case DIRECTION-UP
                    }
                    3 -> {
                        // case CHOSEN-UP
                    }
                }
            }
        }
    }

    private fun wireGameButtons() {
        findViewById<Button>(R.id.start_or_stop).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.start_or_stop).setOnClickListener {
            started = !started
            if (locationServiceActive) {
                // stopping the service
                stopService(Intent(this, LocationService::class.java))

            } else {
                if (Build.VERSION.SDK_INT >= 26) {
                    // starting the FOREGROUND service
                    // service has to display non-dismissable notification within 5 secs
                    startForegroundService(Intent(this, LocationService::class.java))
                } else {
                    startService(Intent(this, LocationService::class.java))
                }

            }
            locationServiceActive = !locationServiceActive
            setColorsAndTexts()
            gameLoop()
        }

        findViewById<Button>(R.id.add_wp).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.add_wp).setOnClickListener {
            add_WP = !add_WP
            add_CP = false
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.add_cp).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.add_cp).setOnClickListener {
            add_CP = !add_CP
            add_WP = false
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.options).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.options).setOnClickListener {
            is_options_toggeled = !is_options_toggeled
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.compass).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.compass).setOnClickListener {
            is_compass_toggeled = !is_compass_toggeled
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.reset).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.reset).setOnClickListener {
            // RESET EVERYTHING
            map.clear()
            WP = null
            CP = null
            setColorsAndTexts()

            findViewById<Button>(R.id.reset).setBackgroundColor(resources.getColor(R.color.colorGreener))
            Handler().postDelayed(
                {
                    findViewById<Button>(R.id.reset).setBackgroundColor(resources.getColor(R.color.colorGreen))
                },
                100 // value in milliseconds
            )
        }

        findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.position).setOnClickListener {
            direction += 1
            direction %= 4

            findViewById<Button>(R.id.position).text = direction_map[direction]
            findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorGreener))
            Handler().postDelayed(
                {
                    findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorGreen))
                },
                100 // value in milliseconds
            )
        }
    }

    private fun gameLoop() {
        Handler().postDelayed(
            {

                val lastUID = stateUID
                if (started && lastUID == stateUID) {
                    headerValues.session_duration += 1
                    updateVisibleText()
                    gameLoop()
                }

            },
            1000 // value in milliseconds
        )
    }

    private fun mapTransformations() {
        when (direction) {
            0 -> {
                // case CENTERED
//                map.animateCamera(CameraUpdateFactory.newLatLngZoom(, 16f));
            }
            1 -> {
                // case NORTH-UP
            }
            2 -> {
                // case DIRECTION-UP
            }
            3 -> {
                // case CHOSEN-UP
            }
        }
    }

    private fun setColorsAndTexts() {
        findViewById<Button>(R.id.start_or_stop).text = if (started) "STOP" else "START"
        findViewById<Button>(R.id.start_or_stop).setBackgroundColor(resources.getColor(if (started) R.color.colorGreener else R.color.colorGreen))
        findViewById<Button>(R.id.add_wp).setBackgroundColor(resources.getColor(if (add_WP) R.color.colorGreener else R.color.colorGreen))
        findViewById<Button>(R.id.add_cp).setBackgroundColor(resources.getColor(if (add_CP) R.color.colorGreener else R.color.colorGreen))
        findViewById<Button>(R.id.options).setBackgroundColor(resources.getColor(if (is_options_toggeled) R.color.colorGreener else R.color.colorGreen))
        findViewById<Button>(R.id.compass).setBackgroundColor(resources.getColor(if (is_compass_toggeled) R.color.colorGreener else R.color.colorGreen))
        if (is_compass_toggeled) {
            findViewById<ImageView>(R.id.imageViewCompass).visibility = View.VISIBLE
        } else {
            image.animation = null
            findViewById<ImageView>(R.id.imageViewCompass).visibility = View.INVISIBLE
        }
    }

    private fun updateVisibleText() {

        fillColumn(
            headerValues.session_duration,
            headerValues.overall_distance_covered,
            R.id.col1,
            String.format(
                "%s:%s:%s", (headerValues.session_duration.toInt() / 3600).toString().padStart(2, '0'),
                ((headerValues.session_duration.toInt() / 60) % 60).toString().padStart(2, '0'),
                (headerValues.session_duration.toInt() % 60).toString().padStart(2, '0')
            )
        )

        fillColumn(
            headerValues.session_duration,
            headerValues.CP_distance_overall,
            R.id.col2,
            ((headerValues.CP_distance_overall * 10).toInt().toDouble() / 10).toString()
        )

        fillColumn(
            headerValues.session_duration,
            headerValues.WP_distance_overall,
            R.id.col3,
            ((headerValues.WP_distance_overall * 10).toInt().toDouble() / 10).toString()
        )

    }

    private fun fillColumn(
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
                round((duration * 1000.0) / (covered * 60.0) * 10) / 10
        }

        findViewById<TextView>(col).text =
            String.format(
                "%s\n%s\n%s",
                covered,
                row2,
                averageSpeed
            )

        return averageSpeed
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        Log.d(TAG, "lifecycle onStart")
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)
        sensorManager.registerListener(this, accelerometer, SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, magnetometer, SENSOR_DELAY_GAME)
        Log.d(TAG, "lifecycle onResume")
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        Log.d(TAG, "lifecycle onPause")
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
        sensorManager.unregisterListener(this, accelerometer)
        sensorManager.unregisterListener(this, magnetometer)
        Log.d(TAG, "lifecycle onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        Log.d(TAG, "lifecycle onDestroy")
    }

    override fun onRestart() {
        super.onRestart()
        Log.d(TAG, "lifecycle onRestart")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
        Log.d(TAG, "lifecycle onLowMemory")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(TAG, "lifecycle onRestoreInstanceState")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d(TAG, "lifecycle onRestoreInstanceState")
    }

    /**
     * Demonstrates converting a [Drawable] to a [BitmapDescriptor],
     * for use as a marker icon.
     */
    private fun vectorToBitmap(@DrawableRes id: Int, width: Int, height: Int): BitmapDescriptor {
        val vectorDrawable: Drawable? = ResourcesCompat.getDrawable(resources, id, null)
        if (vectorDrawable == null) {
            Log.e(TAG, "Resource not found")
            return BitmapDescriptorFactory.defaultMarker()
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor === accelerometer) {
            lowPass(event.values, lastAccelerometer)
            lastAccelerometerSet = true
        } else if (event.sensor === magnetometer) {
            lowPass(event.values, lastMagnetometer)
            lastMagnetometerSet = true
        }

        doCompassThings()
    }

    private fun doCompassThings() {
        if (lastAccelerometerSet && lastMagnetometerSet && is_compass_toggeled) {
            val r = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, null, lastAccelerometer, lastMagnetometer)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val degree = (toDegrees(orientation[0].toDouble()) + 360).toFloat() % 360

                val rotateAnimation = RotateAnimation(
                    currentDegree,
                    -degree,
                    RELATIVE_TO_SELF, 0.5f,
                    RELATIVE_TO_SELF, 0.5f
                )
                rotateAnimation.duration = 1000
                rotateAnimation.fillAfter = true

                image.startAnimation(rotateAnimation)
                currentDegree = -degree
            }
        }
    }

    fun lowPass(input: FloatArray, output: FloatArray) {
        val alpha = 0.05f

        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
    }

    // ============================================== NOTIFICATION CHANNEL CREATION =============================================
    private fun createNotificationChannel() {
        // when on 8 Oreo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                C.NOTIFICATION_CHANNEL,
                "Default channel",
                NotificationManager.IMPORTANCE_LOW
            );

            //.setShowBadge(false).setSound(null, null);

            channel.description = "Default channel"

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    // ============================================== PERMISSION HANDLING =============================================
    // Returns the current state of the permissions needed.
    private fun checkPermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestPermissions() {
        val shouldProvideRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(
                TAG,
                "Displaying permission rationale to provide additional context."
            )

            Snackbar.make(
                findViewById(R.id.activity_main),
                "Hey, i really need to access GPS!",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction("OK", View.OnClickListener {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        C.REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                })
                .show()
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                C.REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == C.REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.count() <= 0 -> { // If user interaction was interrupted, the permission request is cancelled and you
                    // receive empty arrays.
                    Log.i(TAG, "User interaction was cancelled.")
                    Toast.makeText(this, "User interaction was cancelled.", Toast.LENGTH_SHORT)
                        .show()
                }
                grantResults[0] == PackageManager.PERMISSION_GRANTED -> {// Permission was granted.
                    Log.i(TAG, "Permission was granted")
                    hasPermissions = true
                    Toast.makeText(this, "Permission was granted", Toast.LENGTH_SHORT).show()
                }
                else -> { // Permission denied.
                    Snackbar.make(
                        findViewById(R.id.activity_main),
                        "You denied GPS! What can I do?",
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction("Settings", View.OnClickListener {
                            // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri: Uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID, null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        })
                        .show()
                }
            }
        }

    }

    fun buttonWPOnClick(view: View) {
        Log.d(TAG, "buttonWPOnClick")
        sendBroadcast(Intent(C.NOTIFICATION_ACTION_WP))
    }

    fun buttonCPOnClick(view: View) {
        Log.d(TAG, "buttonCPOnClick")
        sendBroadcast(Intent(C.NOTIFICATION_ACTION_CP))
    }

    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, intent!!.action)
            when (intent.action) {
                C.LOCATION_UPDATE_ACTION -> {
                    userLatitude =
                        intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_LATITUDE, 0.0)
                    userLongitude =
                        intent.getDoubleExtra(C.LOCATION_UPDATE_ACTION_LONGITUDE, 0.0)
                }
            }
        }

    }

}
