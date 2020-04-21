package ee.taltech.spormapsapp

import okhttp3.*
import java.io.IOException
import kotlin.reflect.KFunction2

object API {

    const val BASE_URL = "https://sportmap.akaver.com/api/v1"

    const val AUTH_REGISTER = "/Account/Register"
    const val AUTH_LOGIN = "/Account/Login"
    const val GPS_SESSION = "/GpsSessions"
    const val GPS_LOCATIONS = "/GpsLocations"
    const val GPS_LOCATION_TYPES = "/GpsLocationTypes"

    const val REST_LOCATION_ID_LOC = "00000000-0000-0000-0000-000000000001"
    const val REST_LOCATION_ID_WP = "00000000-0000-0000-0000-000000000002"
    const val REST_LOCATION_ID_CP = "00000000-0000-0000-0000-000000000003"

    var token: String? = null
    val JSON: MediaType = MediaType.parse("application/json; charset=utf-8")!!

    private val client = OkHttpClient()

    fun postBodyToUrl(content: String, url: String, callBack: KFunction2<String, Boolean, Unit>) {
        val request = Request.Builder()
            .url(BASE_URL + url)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(JSON, content))

        doCall(request, callBack)
    }

    fun putBodyToUrl(content: String, url: String, callBack: KFunction2<String, Boolean, Unit>) {
        val request = Request.Builder()
            .url(BASE_URL + url)
            .addHeader("Content-Type", "application/json")
            .put(RequestBody.create(JSON, content))

        doCall(request, callBack)
    }

    fun getFromUrl(url: String, callBack: KFunction2<String, Boolean, Unit>) {
        val request = Request.Builder()
            .url(BASE_URL + url)
            .addHeader("Content-Type", "application/json")
            .get()

        doCall(request, callBack)
    }

    fun deleteFromUrl(url: String, callBack: KFunction2<String, Boolean, Unit>) {
        val request = Request.Builder()
            .url(BASE_URL + url)
            .addHeader("Content-Type", "application/json")
            .delete()

        doCall(request, callBack)
    }


    private fun doCall(
        request: Request.Builder,
        callBack: KFunction2<String, Boolean, Unit>
    ) {
        if (token != null) {
            request.addHeader(
                "Authorization Bearer", token!!
            )
        }

        client.newCall(request.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (e.message != null) {
                    callBack(e.message!!, false)
                } else {
                    callBack("Unknown error", false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.body() != null) {
                    if (response.isSuccessful) {
                        callBack(response.body()!!.string(), true)
                    } else {
                        callBack(response.body()!!.string(), false)
                    }
                }
            }

        })
    }
}
