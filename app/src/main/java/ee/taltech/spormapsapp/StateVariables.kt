package ee.taltech.spormapsapp

import android.location.Location

object StateVariables {

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
    var currentLocation: Location? = null

    // other locations
    var locationStart: Location? = null
    var locationCP: Location? = null
    var locationWP: Location? = null

    // doStuff
    var add_WP = false
    var add_CP = false
}