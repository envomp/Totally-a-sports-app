package ee.taltech.spormapsapp.api

import android.content.Context
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley


class WebApiSingletonHandler {
    companion object {
        private val TAG = this::class.java.declaringClass!!.simpleName
        private var instance: WebApiSingletonHandler? = null
        private var mContext: Context? = null

        @Synchronized
        fun getInstance(context: Context): WebApiSingletonHandler {
            if (instance == null) {
                instance =
                    WebApiSingletonHandler(context)
            }
            return instance!!
        }
    }

    constructor(context: Context) {
        mContext = context
    }

    private var requestQueue: RequestQueue? = null
        get() {
            if (field == null) {
                field = Volley.newRequestQueue(mContext)
            }
            return field
        }

    fun addToRequestQueue(request: JsonObjectRequest, b: Boolean) {
        Log.d(TAG, request.url)
        request.tag = TAG
        requestQueue?.add(request)
        if (b) {
            customRetryPolicy(request, 100)
        }
    }

    private fun customRetryPolicy(
        request: JsonObjectRequest,
        i: Int
    ) {
        Handler().postDelayed(
            {
                if (!request.hasHadResponseDelivered() && i != 0) {
                    requestQueue?.add(request)
                    customRetryPolicy(request, i - 1)
                }
            },
            3000 // value in milliseconds
        )
    }

    fun cancelPendingRequest(tag: String) {
        if (requestQueue != null) {

            requestQueue!!.cancelAll(if (TextUtils.isEmpty(tag)) TAG else tag)
        }
    }
}