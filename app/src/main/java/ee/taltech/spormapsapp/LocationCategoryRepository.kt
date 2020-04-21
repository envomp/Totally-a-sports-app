package ee.taltech.spormapsapp

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase


class LocationCategoryRepository(val context: Context) {
    private lateinit var dbHelper: DbHelper
    private lateinit var db: SQLiteDatabase

    fun open(): LocationCategoryRepository {
        dbHelper = DbHelper(context)
        db = dbHelper.writableDatabase

        return this
    }

    fun close() {
        dbHelper.close()
    }

    fun add(LocationCategory: LocationCategory) {
        val values = ContentValues()
        values.put(DbHelper.CATEGORY_ALTITUDE, LocationCategory.altitude)
        values.put(DbHelper.CATEGORY_LATITUDE, LocationCategory.latitude)
        values.put(DbHelper.CATEGORY_LONGITUDE, LocationCategory.longitude)
        values.put(DbHelper.CATEGORY_SESSION, LocationCategory.session)
        values.put(DbHelper.CATEGORY_SPEED, LocationCategory.speed)
        values.put(DbHelper.CATEGORY_TIME, LocationCategory.time)
        values.put(DbHelper.CATEGORY_TYPE, LocationCategory.marker_type)

        db.insert(DbHelper.CATEGORY_TABLE_NAME, null, values)
    }

    private fun fetch(): Cursor {

        var columns = arrayOf(
            DbHelper.CATEGORY_ALTITUDE,
            DbHelper.CATEGORY_LATITUDE,
            DbHelper.CATEGORY_LONGITUDE,
            DbHelper.CATEGORY_SESSION,
            DbHelper.CATEGORY_SPEED,
            DbHelper.CATEGORY_TIME,
            DbHelper.CATEGORY_TYPE
        )

        val orderBy =
            DbHelper.CATEGORY_ID

        return db.query(
            DbHelper.CATEGORY_TABLE_NAME,
            columns,
            null,
            null,
            null,
            null,
            orderBy
        )
    }

    fun getAll(): List<LocationCategory> {
        val toDoCategories = ArrayList<LocationCategory>()
        val cursor = fetch()

        while (cursor.moveToNext()) {
            toDoCategories.add(
                LocationCategory(
                    0,
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_LONGITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_ALTITUDE)),
                    cursor.getLong(cursor.getColumnIndex(DbHelper.CATEGORY_TIME)),
                    cursor.getFloat(cursor.getColumnIndex(DbHelper.CATEGORY_SPEED)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_TYPE)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_SESSION))
                )
            )
        }

        return toDoCategories;
    }
}
