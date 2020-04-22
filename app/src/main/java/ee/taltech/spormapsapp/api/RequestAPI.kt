package ee.taltech.spormapsapp.api

class RequestAPI {

    data class AuthRequestLogin(
        val email: String,
        val password: String
    )

    data class AuthRequestRegister(
        val email: String,
        val password: String,
        val firstName: String,
        val lastName: String
    )

    data class GpsSessions(
        val name: String,
        val description: String,
        val recordedAt: String
    )

    data class GpsLocations(
        val recordedAt: String,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Double,
        val altitude: Double,
        val verticalAccuracy: Double,
        val gpsSessionId: String,
        val gpsLocationTypeId: String
    )

}