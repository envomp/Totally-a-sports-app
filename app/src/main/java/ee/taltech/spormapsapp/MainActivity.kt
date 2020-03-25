package ee.taltech.spormapsapp


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.animation.Animation.RELATIVE_TO_SELF
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.map.*
import java.lang.Math.toDegrees
import kotlin.math.round


class MainActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    private var stateUID = 0
    private lateinit var map: GoogleMap

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
    private var direction = 0
    private val direction_map: HashMap<Int, String> =
        hashMapOf(0 to "CENTERED", 1 to "NORTH-UP", 2 to "DIRECTION-UP", 3 to "CHOSEN-UP")
    private var is_compass_toggeled = false
    private var is_options_toggeled = false

    // Dynamic modifiers
    private var started = false
    private var add_WP = false
    private var add_CP = false

    // COL 1
    private var overall_distance_covered = 0 // meters
    private var session_duration = 0 // seconds
    private var overall_average_speed = 0.0

    //COL 2
    private var CPs: MutableCollection<Marker> = mutableListOf() // capture points__ Permanent!
    private val CP_disance_overall = 0
    private val CP_distance_line = 0
    private var CP_average_speed = 0.0

    // COL 3
    private var WP: Marker? = null // way points__ Only one!
    private val WP_distance_overall = 0
    private val WP_distance_line = 0
    private var WP_average_speed = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        with(mapView) {
            // Initialise the MapView
            onCreate(savedInstanceState)
            // Set the map ready callback to receive the GoogleMap object
            getMapAsync {
                map = it
                MapsInitializer.initialize(applicationContext)
                setMapLocation(it)

                // create bounds that encompass every location we reference

                with(map) {

//                    moveCamera(CameraUpdateFactory)
                }

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
                    .icon(vectorToBitmap(R.drawable.capturepoint, 100, 150))
            )
        }

        var i = 0
        for (marker: Marker in CPs) {
            i += 1
            marker.remove()
            map.addMarker(
                MarkerOptions().position(marker.position)
                    .title(String.format("Added a capture point nr: %s", i))
                    .draggable(true)
                    .icon(vectorToBitmap(R.drawable.capturepoint, 100, 150))
            )
        }

    }

    private fun setMapLocation(map: GoogleMap) {
        with(map) {
//            moveCamera(CameraUpdateFactory.newLatLngZoom(position, 13f))

            mapType = GoogleMap.MAP_TYPE_TERRAIN
            setOnMapClickListener {
                if (add_CP) {

                    CPs.add(
                        addMarker(
                            MarkerOptions().position(LatLng(it.latitude, it.longitude))
                                .title(String.format("Added a capture point nr: %s", CPs.size + 1))
                                .draggable(true)
                                .icon(vectorToBitmap(R.drawable.capturepoint, 100, 150))
                        )
                    )

                    Toast.makeText(
                        this@MainActivity,
                        String.format("Added a capture point nr: %s", CPs.size),
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
        }
    }

    private fun wireGameButtons() {
        findViewById<Button>(R.id.start_or_stop).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.start_or_stop).setOnClickListener {
            started = !started
            setColorsAndTexts()
            gameLoop()
        }

        findViewById<Button>(R.id.add_wp).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.add_wp).setOnClickListener {
            add_WP = !add_WP
            add_CP = false
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.add_cp).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.add_cp).setOnClickListener {
            add_CP = !add_CP
            add_WP = false
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.options).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.options).setOnClickListener {
            is_options_toggeled = !is_options_toggeled
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.compass).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.compass).setOnClickListener {
            is_compass_toggeled = !is_compass_toggeled
            setColorsAndTexts()
        }

        findViewById<Button>(R.id.reset).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.reset).setOnClickListener {
            // RESET EVERYTHING
            map.clear()
            WP = null
            CPs = mutableListOf()
            setColorsAndTexts()

            findViewById<Button>(R.id.reset).setBackgroundColor(resources.getColor(R.color.colorOrangeActive))
            Handler().postDelayed(
                {
                    findViewById<Button>(R.id.reset).setBackgroundColor(resources.getColor(R.color.colorOrange))
                },
                100 // value in milliseconds
            )
        }

        findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorOrange))
        findViewById<Button>(R.id.position).setOnClickListener {
            direction += 1
            direction %= 4

            findViewById<Button>(R.id.position).text = direction_map[direction]
            findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorOrangeActive))
            Handler().postDelayed(
                {
                    findViewById<Button>(R.id.position).setBackgroundColor(resources.getColor(R.color.colorOrange))
                },
                100 // value in milliseconds
            )
        }
    }

    private fun gameLoop() {
        Handler().postDelayed(
            {

                var lastUID = stateUID
                if (started && lastUID == stateUID) {
                    session_duration += 1

                    updateVisibleText()
                    gameLoop()
                }

            },
            1000 // value in milliseconds
        )
    }

    private fun setColorsAndTexts() {
        findViewById<Button>(R.id.start_or_stop).text = if (started) "STOP" else "START"
        findViewById<Button>(R.id.start_or_stop).setBackgroundColor(resources.getColor(if (started) R.color.colorOrangeActive else R.color.colorOrange))
        findViewById<Button>(R.id.add_wp).setBackgroundColor(resources.getColor(if (add_WP) R.color.colorOrangeActive else R.color.colorOrange))
        findViewById<Button>(R.id.add_cp).setBackgroundColor(resources.getColor(if (add_CP) R.color.colorOrangeActive else R.color.colorOrange))
        findViewById<Button>(R.id.options).setBackgroundColor(resources.getColor(if (is_options_toggeled) R.color.colorOrangeActive else R.color.colorOrange))
        findViewById<Button>(R.id.compass).setBackgroundColor(resources.getColor(if (is_compass_toggeled) R.color.colorOrangeActive else R.color.colorOrange))
        if (is_compass_toggeled) {
            findViewById<ImageView>(R.id.imageViewCompass).visibility = View.VISIBLE
        } else {
            image.animation = null
            findViewById<ImageView>(R.id.imageViewCompass).visibility = View.INVISIBLE
        }
    }

    private fun updateVisibleText() {

        overall_average_speed = fillColumn(
            session_duration,
            overall_distance_covered,
            R.id.col1,
            String.format(
                "%s:%s:%s", (session_duration / 3600).toString().padStart(2, '0'),
                ((session_duration / 60) % 60).toString().padStart(2, '0'),
                (session_duration % 60).toString().padStart(2, '0')
            )
        )

        CP_average_speed = fillColumn(
            session_duration,
            CP_disance_overall,
            R.id.col2,
            CP_disance_overall.toString()
        )

        WP_average_speed = fillColumn(
            session_duration,
            WP_distance_overall,
            R.id.col3,
            WP_distance_overall.toString()
        )

    }

    private fun fillColumn(
        sessionDuration: Int,
        overallDistanceCovered: Int,
        col: Int,
        row2: String
    ): Double {

        var averageSpeed = 0.0
        if (overallDistanceCovered != 0) {
            averageSpeed =
                round((sessionDuration * 1000.0) / (overallDistanceCovered * 60.0) * 10) / 10
        }

        findViewById<TextView>(col).text =
            String.format(
                "%s\n%s\n%s",
                overallDistanceCovered,
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
}
