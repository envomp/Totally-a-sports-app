package ee.taltech.spormapsapp.helper


import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Environment
import android.text.TextUtils
import android.util.Patterns
import android.widget.Toast
import androidx.core.app.ActivityCompat
import ee.taltech.spormapsapp.db.LocationCategory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern


object Utils {

    fun isValidEmail(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    fun isValidPassword(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{6,}\$")
            .matcher(target).matches()
    }

    fun isValidPasswordBasic(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}\$").matcher(target)
            .matches()
    }

    fun isValidPasswordMedium(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,}$")
            .matcher(target).matches()
    }

    fun isValidName(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
    }

    fun generateGfx(
        name: String,
        points: List<LocationCategory>
    ): String {
        val header =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?><gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"MapSource 6.15.5\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\"><trk>\n"
        var name = "<name>$name</name><trkseg>\n"
        var segments = ""
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        for (location in points) {
            segments += "<trkpt lat=\"" + location.latitude.toString() +
                    "\" lon=\"" + location.longitude.toString() +
                    "\"><time>" + df.format(Date(location.time.toLong())).toString() +
                    "</time>" + "<type>" + location.marker_type + "</type>" +
                    "</trkpt>\n"
        }
        val footer = "</trkseg></trk></gpx>"

        return header + "\n" + name + "\n" + segments + "\n" + footer + "\n"

    }

    private const val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(
        READ_EXTERNAL_STORAGE,
        WRITE_EXTERNAL_STORAGE
    )

    fun verifyStoragePermissions(activity: Activity?) {
        // Check if we have write permission
        val permission =
            ActivityCompat.checkSelfPermission(
                activity!!,
                WRITE_EXTERNAL_STORAGE
            )
        if (permission != PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        }
    }
}