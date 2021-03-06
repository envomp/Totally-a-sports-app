package ee.taltech.spormapsapp.helper

import android.content.res.Configuration
import android.content.res.Resources
import android.location.Location
import android.widget.RemoteViews
import kotlin.math.roundToInt
import kotlin.random.Random

class StateVariables {

    var stateUID = 215761238 // helps to handle the state
    var state_code: String? = null // backend state
    var sync_interval: Long = 5000

    // COL 1
    var overall_distance_covered = 0f // meters
    var line_distance_covered = 0f // meters
    var session_start = 0L // epoch time
    var session_duration = 0L // seconds
    var overall_average_speed = 0 // min/km

    //COL 2
    var CP_distance_overall = 0f
    var CP_distance_line = 0f
    var CP_average_speed = 0

    // COL 3
    var WP_distance_overall = 0f
    var WP_distance_line = 0f
    var WP_average_speed = 0

    // last received location
    var oldLocation: Location? = null
    var currentLocation: Location? = null

    // other locations
    var locationStart: Location? = null
    var locationCP: Location? = null
    var locationWP: Location? = null


    fun hardReset() {
        stateUID = Random.nextInt()
        state_code = null
        sync_interval = 5000L
        overall_distance_covered = 0f
        line_distance_covered = 0f
        session_start = 0L
        session_duration = 0L
        overall_average_speed = 0
        CP_distance_overall = 0f
        CP_distance_line = 0f
        CP_average_speed = 0
        WP_distance_overall = 0f
        WP_distance_line = 0f
        WP_average_speed = 0
        oldLocation = null
        currentLocation = null
        locationStart = null
        locationCP = null
        locationWP = null
    }

    fun fillColumn(
        notifyview: RemoteViews,
        sessionDuration: Long,
        overallDistanceCovered: Float,
        col: Int,
        row2: String,
        resources: Resources
    ): Int {

        val (averageSpeed, text) = getColumnText(
            sessionDuration,
            overallDistanceCovered,
            row2,
            resources,
            true
        )

        notifyview.setTextViewText(col, text)

        return averageSpeed
    }

    fun getColumnText(
        sessionDuration: Long,
        overallDistanceCovered: Float,
        row2: String,
        resources: Resources,
        hasCapLines: Boolean
    ): Pair<Int, String> {
        val duration = sessionDuration.toInt()
        val covered = overallDistanceCovered.toInt()
        var averageSpeed = 0
        if (covered != 0) {
            averageSpeed = ((duration / 60) / (overallDistanceCovered / 1000)).roundToInt()
        }

        val speedText = if (averageSpeed < 1 || averageSpeed > 99) {
            "--:--"
        } else {
            averageSpeed.toString()
        }

        var new_covered = ""

        var i = 0
        for (char in covered.toString().reversed()) {
            new_covered = if (i % 3 != 0) {
                "$char$new_covered"
            } else {
                "$char $new_covered"
            }
            i++
        }

        val text = String.format(
            "%s\n%s\n%s",
            "${new_covered}m",
            row2,
            "$speedText${if (resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE || hasCapLines) {
                " "
            } else {
                "\n"
            }}min/km"
        )
        return Pair(averageSpeed, text)
    }

    fun update(location: Location) {

        if (currentLocation == null) {
            locationStart = location
        } else {
            line_distance_covered = location.distanceTo(locationStart)
            overall_distance_covered += location.distanceTo(currentLocation)

            if (locationCP == null) {
                CP_distance_line = 0f
            } else {
                CP_distance_line = location.distanceTo(locationCP)
                CP_distance_overall += location.distanceTo(currentLocation)
            }

            if (locationWP == null) {
                WP_distance_line = 0f
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
    }
}