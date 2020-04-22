package ee.taltech.spormapsapp.db

import android.location.Location

class LocationCategory {
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var altitude: Double = 0.0
    var time: String = "0"
    var speed: Float = 0f
    var marker_type: String = "?"
    var session: String = "?"


    constructor(location: Location, markerType: String, session: String) {
        this.altitude = location.altitude
        this.latitude = location.latitude
        this.longitude = location.longitude
        this.time = location.time.toString()
        this.speed = location.speed
        this.marker_type = markerType
        this.session = session
    }

    constructor(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        time: String,
        speed: Float,
        type: String,
        session: String
    ) {
        this.latitude = latitude
        this.longitude = longitude
        this.altitude = altitude
        this.time = time
        this.speed = speed
        this.marker_type = type
        this.session = session
    }

    fun getLocation(): Location {
        val location = Location("DB")
        location.speed = speed
        location.time = time.toLong()
        location.bearing = 0f
        location.speed = speed
        location.latitude = latitude
        location.longitude = longitude
        location.altitude = altitude
        return location
    }

}
