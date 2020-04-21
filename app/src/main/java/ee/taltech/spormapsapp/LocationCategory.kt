package ee.taltech.spormapsapp

import android.location.Location

class LocationCategory {
    var id: Int = 0
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var altitude: Double = 0.0
    var time: Long = 0
    var speed: Float = 0f
    var marker_type: String = "?"
    var session: String = "?"


    constructor(location: Location, markerType: String, session: String) {
        this.altitude = location.altitude
        this.latitude = location.latitude
        this.longitude = location.longitude
        this.time = location.time
        this.speed = location.speed
        this.marker_type = markerType
        this.session = session
    }

    constructor(
        id: Int,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        time: Long,
        speed: Float,
        type: String,
        session: String
    ) {
        this.id = id
        this.latitude = latitude
        this.longitude = longitude
        this.altitude = altitude
        this.time = time
        this.speed = speed
        this.marker_type = type
        this.session = session
    }

}
