package ee.taltech.spormapsapp

import android.location.Location
import android.widget.RemoteViews
import kotlin.math.roundToInt

object StateVariables {

    var stateUID = 215761238 // helps to handle the state

    // COL 1
    var overall_distance_covered = 0f // meters
    var line_distance_covered = 0f // meters
    var session_duration = 0f // seconds
    var overall_average_speed = 0.0

    //COL 2
    var CP_distance_overall = 0f
    var CP_distance_line = 0f
    var CP_average_speed = 0.0

    // COL 3
    var WP_distance_overall = 0f
    var WP_distance_line = 0f
    var WP_average_speed = 0.0

    // last received location
    var oldLocation: Location? = null
    var currentLocation: Location? = null

    // other locations
    var locationStart: Location? = null
    var locationCP: Location? = null
    var locationWP: Location? = null

    // doStuff
    var add_WP = false
    var add_CP = false

    fun fillColumn(
        notifyview: RemoteViews,
        sessionDuration: Float,
        overallDistanceCovered: Float,
        col: Int,
        row2: String
    ): Double {

        val (averageSpeed, text) = getColumnText(sessionDuration, overallDistanceCovered, row2)

        notifyview.setTextViewText(
            col,
            text
        )

        return averageSpeed
    }

    fun getColumnText(
        sessionDuration: Float,
        overallDistanceCovered: Float,
        row2: String
    ): Pair<Double, String> {
        val duration = sessionDuration.toInt()
        val covered = overallDistanceCovered.toInt()
        var averageSpeed = 0.0
        if (covered != 0) {
            averageSpeed =
                (((duration * 1000.0) / (overallDistanceCovered * 60.0) * 10).roundToInt() / 10).toDouble()
        }

        val text = String.format(
            "%s\n%s\n%s",
            "$covered m",
            row2,
            "$averageSpeed m/km"
        )
        return Pair(averageSpeed, text)
    }
}