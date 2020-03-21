package ee.taltech.spormapsapp


import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.header.*
import kotlinx.android.synthetic.main.map.*


class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
    }

    val position = LatLng(-33.920455, 18.466941)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        with(mapView) {
            // Initialise the MapView
            onCreate(savedInstanceState)
            // Set the map ready callback to receive the GoogleMap object
            getMapAsync{
                MapsInitializer.initialize(applicationContext)
                setMapLocation(it)
            }
        }
    }

    private fun setMapLocation(map : GoogleMap) {
        with(map) {
            moveCamera(CameraUpdateFactory.newLatLngZoom(position, 13f))
            addMarker(MarkerOptions().position(position))
            mapType = GoogleMap.MAP_TYPE_NORMAL
            setOnMapClickListener {
                Toast.makeText(this@MainActivity, "Clicked on map", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        Log.d(TAG, "lifecycle onStart")
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
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

}
