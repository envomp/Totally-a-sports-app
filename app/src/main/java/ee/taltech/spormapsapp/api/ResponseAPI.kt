package ee.taltech.spormapsapp.api

class ResponseAPI {
    data class AuthResponse(
        val token: String,
        val status: String
    )

    data class GpsSessions(
        val name: String,
        val description: String,
        val recordedAt: String,
        val duration: Double,
        val speed: Double,
        val distance: Double,
        val climb: Double,
        val descent: Double,
        val appUserId: String,
        val id: String
    )

    //////////////////////   GET GpsLocationTypes

    data class GpsLocationTypes(
        val data: List<GpsLocationType>
    )

    data class GpsLocationType(
        val name: String,
        val description: String,
        val id: String
    )
}
