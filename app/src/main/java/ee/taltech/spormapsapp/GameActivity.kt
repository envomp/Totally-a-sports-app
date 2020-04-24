package ee.taltech.spormapsapp

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.animation.Animation.RELATIVE_TO_SELF
import android.view.animation.RotateAnimation
import android.widget.*
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.edmodo.rangebar.RangeBar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.CancelableCallback
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import ee.taltech.spormapsapp.api.API
import ee.taltech.spormapsapp.db.DataRecyclerViewAdapterCategories
import ee.taltech.spormapsapp.db.LocationAlias
import ee.taltech.spormapsapp.db.LocationCategory
import ee.taltech.spormapsapp.db.LocationCategoryRepository
import ee.taltech.spormapsapp.helper.C
import ee.taltech.spormapsapp.helper.StateVariables
import ee.taltech.spormapsapp.helper.StateVariables.CP_average_speed
import ee.taltech.spormapsapp.helper.StateVariables.CP_distance_line
import ee.taltech.spormapsapp.helper.StateVariables.CP_distance_overall
import ee.taltech.spormapsapp.helper.StateVariables.WP_average_speed
import ee.taltech.spormapsapp.helper.StateVariables.WP_distance_line
import ee.taltech.spormapsapp.helper.StateVariables.WP_distance_overall
import ee.taltech.spormapsapp.helper.StateVariables.currentLocation
import ee.taltech.spormapsapp.helper.StateVariables.line_distance_covered
import ee.taltech.spormapsapp.helper.StateVariables.locationCP
import ee.taltech.spormapsapp.helper.StateVariables.locationStart
import ee.taltech.spormapsapp.helper.StateVariables.locationWP
import ee.taltech.spormapsapp.helper.StateVariables.overall_average_speed
import ee.taltech.spormapsapp.helper.StateVariables.overall_distance_covered
import ee.taltech.spormapsapp.helper.StateVariables.session_duration
import ee.taltech.spormapsapp.helper.StateVariables.session_start
import ee.taltech.spormapsapp.helper.StateVariables.stateUID
import ee.taltech.spormapsapp.helper.StateVariables.state_code
import ee.taltech.spormapsapp.helper.StateVariables.sync_interval
import kotlinx.android.synthetic.main.map.*
import kotlinx.android.synthetic.main.options.*
import java.lang.Exception
import java.lang.Math.*
import kotlin.collections.HashMap
import kotlin.math.roundToLong
import kotlin.random.Random.Default.nextInt


class GameActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    // DB
    private lateinit var databaseConnector: LocationCategoryRepository

    // async
    private val broadcastReceiver = InnerBroadcastReceiver()
    private val broadcastReceiverIntentFilter: IntentFilter = IntentFilter()
    private var locationServiceActive = false

    // sensors
    lateinit var sensorManager: SensorManager
    lateinit var image: ImageView
    lateinit var accelerometer: Sensor
    lateinit var magnetometer: Sensor

    // sensor data
    var currentDegree = 0.0f
    var actualDegree = 0.0f
    var lastAccelerometer = FloatArray(3)
    var lastMagnetometer = FloatArray(3)
    var lastAccelerometerSet = false
    var lastMagnetometerSet = false

    // Header
    private var direction = 0
    private val directionMap: HashMap<Int, String> =
        hashMapOf(0 to "CENTERED", 1 to "NORTH-UP", 2 to "DIRECTION-UP", 3 to "CHOSEN-UP")
    private var isCompassToggled = false
    private var isOptionsToggled = false

    // Dynamic modifiers
    private var hasPermissions = false
    private var started = false

    // map
    private var finishedAnimation = true
    private lateinit var map: GoogleMap
    private var CP: MutableList<Marker> = mutableListOf() // capture points
    private var WP: Marker? = null // way points. Only one!
    private var SP: Marker? = null // way points. Only one!
    private var polylines: MutableList<LocationCategory> = mutableListOf() // path

    private var minGradient = 0.0
    private var maxGradient = 30.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        databaseConnector = LocationCategoryRepository(this).open()

        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION)
        broadcastReceiverIntentFilter.addAction(C.SESSION_START_ACTION)
        broadcastReceiverIntentFilter.addAction(C.SESSION_END_ACTION)
        broadcastReceiverIntentFilter.addAction(C.SESSION_START_FAILED)
        broadcastReceiverIntentFilter.addAction(C.DB_UPDATE)
        broadcastReceiverIntentFilter.addAction(C.DISPLAY_SESSION)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_WP)
        broadcastReceiverIntentFilter.addAction(C.LOCATION_UPDATE_ACTION_CP)


        registerReceiver(broadcastReceiver, broadcastReceiverIntentFilter)

        recyclerViewCategories.layoutManager = LinearLayoutManager(this)
        recyclerViewCategories.adapter = DataRecyclerViewAdapterCategories(this, databaseConnector)

        // safe to call every time
        createNotificationChannel()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            hasPermissions = true
        }

        with(mapView) {
            // Initialise the MapView
            onCreate(savedInstanceState)
            // Set the map ready callback to receive the GoogleMap object
            getMapAsync {
                map = it
                map.clear()
                MapsInitializer.initialize(applicationContext)
                if (hasPermissions) {
                    map.isMyLocationEnabled = true
                }
                mapActions(it)

                // create bounds that encompass every location we reference
                cameraFocusPosition(map)

                if (LocationService.isServiceCreated()) {

                    try {
                        started = true
                        locationServiceActive = true
                        if (hasPermissions) {
                            currentLocation = map.myLocation
                        }
                        drawSession(state_code!!)
                        startGameLoop()
                    } catch (e: Exception) {
                        started = false
                        locationServiceActive = false
                        stopService(Intent(this@GameActivity, LocationService::class.java))
                        StateVariables.hardReset()
                        updateVisibleText()
                        setColorsAndTexts()
                    }
                }
            }
        }

        image = findViewById(R.id.imageViewCompass)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(TYPE_MAGNETIC_FIELD)

        wireGameButtons()
        updateVisibleText()

    }

    private fun mapActions(
        map: GoogleMap
    ) {
        with(map) {

            mapType = GoogleMap.MAP_TYPE_TERRAIN

            map.setOnMyLocationButtonClickListener {
                map.stopAnimation()
                animateCamera(currentLocation, map, null)
                true
            }
        }
    }

    private fun GoogleMap.addWayPoint() {
        if (locationWP != null) {
            WP?.remove()
            addWayPointMarker()

            Toast.makeText(this@GameActivity, "Added a way point", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun GoogleMap.addWayPointMarker() {
        WP = addMarker(
            MarkerOptions().position(LatLng(locationWP!!.latitude, locationWP!!.longitude))
                .title("Way point")
                .icon(vectorToBitmap(R.drawable.waypoint, 100, 150))
        )
    }

    private fun GoogleMap.addCheckPoint() {
        if (locationCP != null) {
            addCheckPointMarker()

            Toast.makeText(
                this@GameActivity,
                String.format("Added a capture point nr. " + (CP.size)),
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

    private fun GoogleMap.addCheckPointMarker() {
        CP.add(
            addMarker(
                MarkerOptions().position(LatLng(locationCP!!.latitude, locationCP!!.longitude))
                    .title(String.format("Capture point nr. " + (CP.size + 1)))
                    .icon(vectorToBitmap(R.drawable.capturepoint, 100, 150))
            )
        )
    }

    private fun mapTransformation(
        location: Location,
        map: GoogleMap
    ) {
        if (locationServiceActive) {

            if (isCompassToggled) {
                location.bearing = actualDegree
            }

            when (direction) {
                0 -> {
                    // case CENTERED
                    cameraFocusPosition(map)
                    animateCamera(location, map, null)
                }
                1 -> {
                    // case NORTH-UP
                    cameraFocusRotation(map)
                    animateCamera(null, map, 0f)
                }
                2 -> {
                    // case DIRECTION-UP
                    cameraFocusRotation(map)
                    animateCamera(
                        null,
                        map,
                        if (location.hasBearing()) location.bearing else null
                    )
                }
                3 -> {
                    // case CHOSEN-UP
                    cameraFocusRotation(map)
                    animateCamera(
                        null,
                        map,
                        if (locationWP != null) location.bearingTo(locationWP) else null
                    )
                }
            }
        }

    }

    private fun cameraFocusPosition(map: GoogleMap) {
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isScrollGesturesEnabledDuringRotateOrZoom = false
    }

    private fun cameraFocusRotation(map: GoogleMap) {
        map.uiSettings.isMyLocationButtonEnabled = true
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isScrollGesturesEnabledDuringRotateOrZoom = true
    }

    private fun animateCamera(
        location: Location?,
        map: GoogleMap,
        bearing: Float?
    ) {

        if (finishedAnimation) {
            val oldPos: CameraPosition = map.cameraPosition

            val pos = CameraPosition.builder(oldPos)
                .zoom(
                    map.cameraPosition.zoom.coerceAtLeast(
                        14f
                    )
                )

            if (location != null) {
                pos.target(
                    LatLng(
                        location.latitude,
                        location.longitude
                    )
                )
            }

            if (bearing != null) {
                pos.bearing(bearing)
            }

            finishedAnimation = false
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos.build()),
                object : CancelableCallback {
                    override fun onFinish() {
                        finishedAnimation = true
                    }

                    override fun onCancel() {
                        finishedAnimation = true
                    }
                })
        }
    }

    private fun wireGameButtons() {
        findViewById<Button>(R.id.start_or_stop).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.start_or_stop).setOnClickListener {
            if (!hasPermissions) {
                Toast.makeText(
                    this@GameActivity,
                    "Can't track you without permissions!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                if (locationServiceActive) {
                    // stopping the service

                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    builder.setTitle("Please enter a name for your session")

                    val input = EditText(this)

                    input.inputType = InputType.TYPE_CLASS_TEXT
                    builder.setView(input)

                    builder.setPositiveButton(
                        "Use custom alias"
                    ) { _, _ ->
                        run {
                            val session = if (state_code == null) {
                                "No Session"
                            } else {
                                state_code!!
                            }

                            shutServiceDown(session, input.text.toString())
                            started = false
                        }
                    }

                    builder.setNegativeButton(
                        "Use Existing"
                    ) { dialog, _ ->
                        run {
                            dialog.cancel()

                            val session = if (state_code == null) {
                                "No Session"
                            } else {
                                state_code!!
                            }

                            shutServiceDown(session, session)
                            started = false
                        }
                    }

                    builder.setNeutralButton(
                        "Cancel"
                    ) { dialog, _ ->
                        run {
                            dialog.cancel()
                            setColorsAndTexts()
                        }
                    }

                    builder.show()

                } else {
                    val interval =
                        findViewById<EditText>(R.id.syncInterval).text.toString().toLongOrNull()

                    if (interval != null) {
                        sync_interval = kotlin.math.max(interval, 1) * 1000
                    } else {
                        sync_interval = 2000
                    }

                    locationStart = map.myLocation
                    if (Build.VERSION.SDK_INT >= 26) {
                        // starting the FOREGROUND service
                        // service has to display non-dismissable notification within 5 secs
                        startForegroundService(Intent(this, LocationService::class.java))
                    } else {
                        startService(Intent(this, LocationService::class.java))
                        val intent = Intent(C.SESSION_TOKEN)
                        intent.putExtra(C.SESSION_TOKEN, API.token)
                        PendingIntent.getBroadcast(this@GameActivity, 0, intent, nextInt(0, 1000))
                            .send()
                    }

                    started = true
                }
                setColorsAndTexts()
            }
        }

        findViewById<Button>(R.id.add_wp).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.add_wp).setOnClickListener {

            if (started && currentLocation != null) {
                val location = Location(currentLocation)
                locationWP = location

                // broadcast to service so it can be added to backend
                val intent = Intent(C.NOTIFICATION_ACTION_WP)
                PendingIntent.getBroadcast(this@GameActivity, 0, intent, 0).send()
            } else {
                Toast.makeText(
                    this@GameActivity,
                    "Start the game to add WP",
                    Toast.LENGTH_SHORT
                ).show()
            }
            setColorsAndTexts()

        }

        findViewById<Button>(R.id.add_cp).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.add_cp).setOnClickListener {

            if (started && currentLocation != null) {
                locationCP = Location(currentLocation)

                // broadcast to service so it can be added to backend
                val intent = Intent(C.NOTIFICATION_ACTION_CP)
                PendingIntent.getBroadcast(this@GameActivity, 0, intent, 0).send()

            } else {
                Toast.makeText(
                    this@GameActivity,
                    "Start the game to add CP",
                    Toast.LENGTH_SHORT
                ).show()
            }
            setColorsAndTexts()

        }

        findViewById<Button>(R.id.options).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.options).setOnClickListener {
            isOptionsToggled = !isOptionsToggled
            isCompassToggled = false
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.compass).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.compass).setOnClickListener {
            isCompassToggled = !isCompassToggled
            isOptionsToggled = false
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.position).setOnClickListener {
            direction += 1
            direction %= 4
            setColorsAndTexts()

            findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorGreener))
            Handler().postDelayed(
                {
                    findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorGreen))
                },
                100 // value in milliseconds
            )
        }

        val bar = findViewById<RangeBar>(R.id.gradientRangeBar)
        bar.setTickCount(30)
        bar.setTickHeight(0f)

        bar.setOnRangeBarChangeListener { _, i, i2 ->
            run {
                barLeft.text = i.toString()
                barRight.text = i2.toString()

                minGradient = i.toDouble()
                maxGradient = i2.toDouble()
            }
        }
    }

    private fun shutServiceDown(
        session: String,
        alias: String
    ) {
        databaseConnector.addAlias(
            LocationAlias(
                session,
                alias,
                databaseConnector.getNumberOfSessionLocations(session),
                System.currentTimeMillis().toString()
            )
        )

        recyclerViewCategories.adapter =
            DataRecyclerViewAdapterCategories(this, databaseConnector)

        stopService(Intent(this, LocationService::class.java))

        StateVariables.hardReset()
        updateVisibleText()
        setColorsAndTexts()

    }

    private fun gameLoop(lastUID: Int) {
        Handler().postDelayed(
            {

                if (lastUID == stateUID) {

                    updateVisibleText()

                    if (hasPermissions && currentLocation != null) {
                        mapTransformation(currentLocation!!, map)
                    }

                    gameLoop(lastUID)

                }

            },
            100 // value in milliseconds
        )
    }

    private fun getTrackColor(used_location: Location?, new_location: Location): Int {
        var color = 0.0

        if (used_location != null && used_location.distanceTo(new_location) > 0.1f) {
            color =
                (used_location.distanceTo(new_location)) / ((new_location.time - used_location.time).toDouble() / 1000)
        }

        color = min(color - minGradient, maxGradient - minGradient) / (maxGradient - minGradient)

        return (0xff000000 + (color * 255).toInt() * pow(16.0, 4.0).toInt()).toInt()
    }

    private fun setColorsAndTexts() {
        findViewById<Button>(R.id.start_or_stop).text =
            if (started && locationServiceActive) {
                "STOP"
            } else if (started) {
                "PENDING"
            } else {
                "START"
            }
        findViewById<Button>(R.id.position).text = directionMap[direction]
        findViewById<Button>(R.id.start_or_stop).setBackgroundColor(resources.getColor(if (started) R.color.colorGreener else R.color.colorGreen))
        findViewById<Button>(R.id.add_wp).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.add_cp).setBackgroundColor(resources.getColor(R.color.colorGreen))
        findViewById<Button>(R.id.options).setBackgroundColor(resources.getColor(if (isOptionsToggled) R.color.colorGreener else R.color.colorGreen))
        findViewById<Button>(R.id.compass).setBackgroundColor(resources.getColor(if (isCompassToggled) R.color.colorGreener else R.color.colorGreen))

        if (isCompassToggled) {
            findViewById<ImageView>(R.id.imageViewCompass).visibility = View.VISIBLE
        } else {
            image.animation = null
            findViewById<ImageView>(R.id.imageViewCompass).visibility = View.GONE
        }

        if (isOptionsToggled) {
            findViewById<View>(R.id.options_drawer).visibility = View.VISIBLE

            val d = resources.getDrawable(R.drawable.rectangle)
            d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            findViewById<View>(R.id.options_drawer).background = d
        } else {
            findViewById<View>(R.id.options_drawer).visibility = View.GONE
            findViewById<View>(R.id.options_drawer).background = null
        }

        if (started) {
            findViewById<View>(R.id.position).visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.position).visibility = View.GONE
        }

    }

    private fun updateVisibleText() {

        session_duration =
            if (session_start == 0L) 0 else (System.currentTimeMillis() - session_start) / 1000

        fillColumn(
            session_duration,
            overall_distance_covered,
            R.id.col1,
            String.format(
                "%s:%s:%s",
                (session_duration.toInt() / 3600).toString().padStart(2, '0'),
                ((session_duration.toInt() / 60) % 60).toString().padStart(2, '0'),
                (session_duration.toInt() % 60).toString().padStart(2, '0')
            )
        )

        fillColumn(
            session_duration,
            CP_distance_overall,
            R.id.col2,
            "${((CP_distance_line * 10).toInt().toDouble() / 10)} m"
        )

        fillColumn(
            session_duration,
            WP_distance_overall,
            R.id.col3,
            "${((WP_distance_line * 10).toInt().toDouble() / 10)} m"
        )

    }

    private fun fillColumn(
        sessionDuration: Long,
        overallDistanceCovered: Float,
        col: Int,
        row2: String
    ): Int {

        val (averageSpeed, text) = StateVariables.getColumnText(
            sessionDuration,
            overallDistanceCovered,
            row2
        )

        findViewById<TextView>(col).text = text
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
        sensorManager.unregisterListener(this, accelerometer)
        sensorManager.unregisterListener(this, magnetometer)
        Log.d(TAG, "lifecycle onStop")

    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()

        // close DB connection
        databaseConnector.close()

        // don't forget to unregister brodcast receiver!!!!
        unregisterReceiver(broadcastReceiver)

        Log.d(TAG, "lifecycle onDestroy")
    }

    override fun finish() {
        super.finish()

        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
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
        Log.d(TAG, "lifecycle onSaveInstanceState")

        outState.putFloat("overall_distance_covered", overall_distance_covered)
        outState.putFloat("line_distance_covered", line_distance_covered)
        outState.putLong("session_duration", session_duration)
        outState.putLong("session_start", session_start)
        outState.putInt("overall_average_speed", overall_average_speed)

        outState.putFloat("CP_distance_overall", CP_distance_overall)
        outState.putFloat("CP_distance_line", CP_distance_line)
        outState.putInt("CP_average_speed", CP_average_speed)

        outState.putFloat("WP_distance_overall", WP_distance_overall)
        outState.putFloat("WP_distance_line", WP_distance_line)
        outState.putInt("WP_average_speed", WP_average_speed)

        outState.putBoolean("locationServiceActive", locationServiceActive)

        outState.putFloat("currentDegree", currentDegree)
        outState.putFloat("actualDegree ", actualDegree)
        outState.putBoolean("is_compass_toggeled", isCompassToggled)
        outState.putBoolean("is_options_toggeled", isOptionsToggled)
        outState.putBoolean("hasPermissions", hasPermissions)
        outState.putBoolean("started", started)
        outState.putInt("direction", direction)

        outState.putString("state_code", state_code)
        outState.putString("token", API.token)

    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d(TAG, "lifecycle onRestoreInstanceState")
        stateUID = (random() * 100000).toInt()

        overall_distance_covered = savedInstanceState.getFloat("overall_distance_covered")
        line_distance_covered = savedInstanceState.getFloat("line_distance_covered")
        session_duration = savedInstanceState.getLong("session_duration")
        session_start = savedInstanceState.getLong("session_start")
        overall_average_speed = savedInstanceState.getInt("overall_average_speed")

        CP_distance_overall = savedInstanceState.getFloat("CP_distance_overall")
        CP_distance_line = savedInstanceState.getFloat("CP_distance_line")
        CP_average_speed = savedInstanceState.getInt("CP_average_speed")

        WP_distance_overall = savedInstanceState.getFloat("WP_distance_overall")
        WP_distance_line = savedInstanceState.getFloat("WP_distance_line")
        WP_average_speed = savedInstanceState.getInt("WP_average_speed")

        locationServiceActive = savedInstanceState.getBoolean("locationServiceActive")

        currentDegree = savedInstanceState.getFloat("currentDegree")
        actualDegree = savedInstanceState.getFloat("actualDegree")
        isCompassToggled = savedInstanceState.getBoolean("is_compass_toggeled")
        isOptionsToggled = savedInstanceState.getBoolean("is_options_toggeled")
        hasPermissions = savedInstanceState.getBoolean("hasPermissions")
        started = savedInstanceState.getBoolean("started")
        direction = savedInstanceState.getInt("direction")

        API.token = savedInstanceState.getString("token")
        state_code = savedInstanceState.getString("state_code")

        finishedAnimation = true

        resumeSession(savedInstanceState)

    }

    private fun resumeSession(savedInstanceState: Bundle) {
        with(mapView) {
            // Initialise the MapView
            onCreate(savedInstanceState)
            // Set the map ready callback to receive the GoogleMap object
            getMapAsync {
                map = it
                MapsInitializer.initialize(applicationContext)
                if (hasPermissions) {
                    map.isMyLocationEnabled = true
                }
                mapActions(it)

                if (locationServiceActive && state_code != null) {
                    startGameLoop()
                } else {
                    started = false
                    locationServiceActive = false
                }
            }
        }
    }

    private fun addStartingPointMarker() {
        if (locationStart != null) {
            SP = map.addMarker(
                MarkerOptions().position(
                    LatLng(
                        locationStart!!.latitude,
                        locationStart!!.longitude
                    )
                )
                    .title("Starting point")
                    .icon(vectorToBitmap(R.drawable.startingpoint, 100, 150))
            )
        }
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
        if (lastAccelerometerSet && lastMagnetometerSet && isCompassToggled) {
            val r = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, null, lastAccelerometer, lastMagnetometer)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                actualDegree = (toDegrees(orientation[0].toDouble()) + 360).toFloat() % 360

                val rotateAnimation = RotateAnimation(
                    currentDegree - windowManager.defaultDisplay.rotation * 90,
                    actualDegree - windowManager.defaultDisplay.rotation * 90,
                    RELATIVE_TO_SELF, 0.5f,
                    RELATIVE_TO_SELF, 0.5f
                )
                rotateAnimation.duration = 1000
                rotateAnimation.fillAfter = true

                image.startAnimation(rotateAnimation)
                currentDegree = -actualDegree
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
                findViewById(R.id.activity_game),
                "Hey, I really need to access GPS!",
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
                grantResults[0] == PackageManager.PERMISSION_GRANTED -> { // Permission was granted.
                    Log.i(TAG, "Permission was granted")
                    hasPermissions = true
                    Toast.makeText(this, "Permission was granted", Toast.LENGTH_SHORT).show()
                }
                else -> { // Permission denied.
                    Snackbar.make(
                        findViewById(R.id.activity_game),
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

    private inner class InnerBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, intent!!.action)
            when (intent.action) {
                C.DB_UPDATE -> {

                    recyclerViewCategories.adapter =
                        DataRecyclerViewAdapterCategories(this@GameActivity, databaseConnector)

                }
                C.DISPLAY_SESSION -> {

                    if (started) {
                        Toast.makeText(
                            this@GameActivity,
                            String.format("To display older sessions\nFinish the current session first!"),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    } else {
                        val hash = intent.getStringExtra(C.DISPLAY_SESSION_HASH)

                        session_duration = 0L
                        drawSession(hash!!)

                        cameraFocusPosition(map)
                        animateCamera(locationStart, map, null)
                    }
                }

                C.SESSION_START_ACTION -> {

                    Toast.makeText(
                        this@GameActivity,
                        "Session started!",
                        Toast.LENGTH_SHORT
                    ).show()

                    state_code = intent.getStringExtra(C.DISPLAY_SESSION_HASH)!!
                    startGameLoop()

                }

                C.SESSION_END_ACTION -> {

                    Toast.makeText(
                        this@GameActivity,
                        "Session ended!",
                        Toast.LENGTH_SHORT
                    ).show()

                    locationServiceActive = false
                    started = false

                    StateVariables.hardReset()
                    updateVisibleText()
                    setColorsAndTexts()
                }

                C.SESSION_START_FAILED -> {
                    started = false
                    stopService(Intent(this@GameActivity, LocationService::class.java))

                    Toast.makeText(
                        this@GameActivity,
                        "No internet!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                C.LOCATION_UPDATE_ACTION -> {

                    val oldLocation =
                        intent.extras?.get(C.LOCATION_UPDATE_ACTION_LOCATION_OLD) as Location?
                    val newLocation =
                        intent.extras!![C.LOCATION_UPDATE_ACTION_LOCATION] as Location

                    val color = getTrackColor(oldLocation, newLocation)

                    polylines.add(
                        LocationCategory(
                            newLocation,
                            "LOC",
                            if (state_code == null) "Current state" else state_code!!
                        )
                    )
                    if (oldLocation != null) {
                        map.addPolyline(
                            PolylineOptions().add(
                                LatLng(oldLocation.latitude, oldLocation.longitude),
                                LatLng(newLocation.latitude, newLocation.longitude)
                            ).color(color)
                        )
                    }
                }

                C.LOCATION_UPDATE_ACTION_CP -> {
                    locationCP = intent.extras!![C.LOCATION_UPDATE_ACTION_CP] as Location
                    map.addCheckPoint()
                }

                C.LOCATION_UPDATE_ACTION_WP -> {
                    locationWP = intent.extras!![C.LOCATION_UPDATE_ACTION_WP] as Location
                    map.addWayPoint()
                }
            }
        }
    }

    private fun startGameLoop() {
        stateUID = (random() * 100000).toInt()
        session_start = System.currentTimeMillis()
        locationServiceActive = true
        started = true

        if (state_code != null) {
            drawSession(state_code!!)
        } else {
            map.clear()
        }

        setColorsAndTexts()
        gameLoop(stateUID)
    }

    private fun drawSession(hash: String) {
        map.clear()
        CP = mutableListOf()
        WP = null
        SP = null
        polylines = mutableListOf()

        var lastLocation: Location? = null

        for (location in databaseConnector.getAllSessionLocations(hash)) {

            if (location.marker_type == "CP") {
                locationCP = location.getLocation()
                map.addCheckPointMarker()
            }

            if (location.marker_type == "WP") {
                locationWP = location.getLocation()
                map.addWayPointMarker()
            }

            if (lastLocation != null) {
                polylines.add(location)

            } else {
                locationStart = location.getLocation()
                addStartingPointMarker()
                polylines.add(location)
            }

            lastLocation = location.getLocation()

        }

        var last: LocationCategory? = null
        for (location in polylines) {
            if (last != null) {
                val color = getTrackColor(last.getLocation(), location.getLocation())

                map.addPolyline(
                    PolylineOptions().add(
                        LatLng(last.latitude, last.longitude),
                        LatLng(location.latitude, location.longitude)
                    ).color(color)
                )
            }
            last = location
        }

    }

}
