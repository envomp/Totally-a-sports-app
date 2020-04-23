package ee.taltech.spormapsapp.db

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
        dbHelper.onCreate(db) // Just in case

        return this
    }

    fun close() {
        dbHelper.close()
    }

    fun addLocation(LocationCategory: LocationCategory) {
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

    fun addAlias(LocationCategoryParser: LocationAlias) {
        val values = ContentValues()
        values.put(DbHelper.ALIAS_SESSION, LocationCategoryParser.hash)
        values.put(DbHelper.ALIAS_ALIAS, LocationCategoryParser.alias)
        values.put(DbHelper.ALIAS_LOCATIONS, LocationCategoryParser.locations)
        values.put(DbHelper.ALIAS_FIRST_DATE, LocationCategoryParser.firstDate)

        db.insert(DbHelper.ALIAS_TABLE_NAME, null, values)
    }

    private fun fetchAliases(): Cursor {

        val columns = arrayOf(
            DbHelper.ALIAS_ALIAS,
            DbHelper.ALIAS_SESSION,
            DbHelper.ALIAS_LOCATIONS,
            DbHelper.ALIAS_FIRST_DATE
        )

        val orderBy = DbHelper.ALIAS_ID + " DESC"

        return db.query(
            DbHelper.ALIAS_TABLE_NAME,
            columns,
            null,
            null,
            null,
            null,
            orderBy
        )
    }

    private fun fetchSessionLocations(session: String): Cursor {

        val columns = arrayOf(
            DbHelper.CATEGORY_ALTITUDE,
            DbHelper.CATEGORY_LATITUDE,
            DbHelper.CATEGORY_LONGITUDE,
            DbHelper.CATEGORY_SESSION,
            DbHelper.CATEGORY_SPEED,
            DbHelper.CATEGORY_TIME,
            DbHelper.CATEGORY_TYPE
        )

        val orderBy = DbHelper.CATEGORY_ID

        return db.query(
            DbHelper.CATEGORY_TABLE_NAME,
            columns,
            "${DbHelper.CATEGORY_SESSION} = '$session'",
            null,
            null,
            null,
            orderBy
        )
    }

    private fun fetchLocations(): Cursor {

        val columns = arrayOf(
            DbHelper.CATEGORY_ALTITUDE,
            DbHelper.CATEGORY_LATITUDE,
            DbHelper.CATEGORY_LONGITUDE,
            DbHelper.CATEGORY_SESSION,
            DbHelper.CATEGORY_SPEED,
            DbHelper.CATEGORY_TIME,
            DbHelper.CATEGORY_TYPE
        )

        val orderBy = DbHelper.CATEGORY_ID

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

    private fun fetchNumberOfLocations(code: String): Cursor {

        val columns = arrayOf(
            DbHelper.CATEGORY_ALTITUDE,
            DbHelper.CATEGORY_LATITUDE,
            DbHelper.CATEGORY_LONGITUDE,
            DbHelper.CATEGORY_SESSION,
            DbHelper.CATEGORY_SPEED,
            DbHelper.CATEGORY_TIME,
            DbHelper.CATEGORY_TYPE
        )

        val orderBy = DbHelper.CATEGORY_ID

        return db.query(
            DbHelper.CATEGORY_TABLE_NAME,
            columns,
            "${DbHelper.ALIAS_SESSION} = '$code'",
            null,
            null,
            null,
            orderBy
        )
    }

    fun getAllSessionLocations(session: String): List<LocationCategory> {
        val locations = ArrayList<LocationCategory>()
        val cursor = fetchSessionLocations(session)

        while (cursor.moveToNext()) {
            locations.add(
                LocationCategory(
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_LONGITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_ALTITUDE)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_TIME)),
                    cursor.getFloat(cursor.getColumnIndex(DbHelper.CATEGORY_SPEED)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_TYPE)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_SESSION))
                )
            )
        }

        return locations;
    }

    fun getAllLocations(): List<LocationCategory> {
        val locations = ArrayList<LocationCategory>()
        val cursor = fetchLocations()

        while (cursor.moveToNext()) {
            locations.add(
                LocationCategory(
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_LONGITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(DbHelper.CATEGORY_ALTITUDE)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_TIME)),
                    cursor.getFloat(cursor.getColumnIndex(DbHelper.CATEGORY_SPEED)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_TYPE)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY_SESSION))
                )
            )
        }

        return locations;
    }

    fun getNumberOfSessionLocations(code: String): Int {
        val cursor = fetchNumberOfLocations(code)
        var count = 0

        while (cursor.moveToNext()) {
            count++
        }

        return count
    }

    fun getAllAliases(): List<LocationAlias> {
        val aliases = ArrayList<LocationAlias>()
        val cursor = fetchAliases()

        while (cursor.moveToNext()) {
            aliases.add(
                LocationAlias(
                    cursor.getString(cursor.getColumnIndex(DbHelper.ALIAS_SESSION)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.ALIAS_ALIAS)),
                    cursor.getInt(cursor.getColumnIndex(DbHelper.ALIAS_LOCATIONS)),
                    cursor.getString(cursor.getColumnIndex(DbHelper.ALIAS_FIRST_DATE))
                )
            )
        }

        return aliases
    }

    fun deleteByHash(hash: String) {
        db.delete(
            DbHelper.CATEGORY_TABLE_NAME,
            "${DbHelper.CATEGORY_SESSION} = '$hash'",
            null
        )

        db.delete(
            DbHelper.ALIAS_TABLE_NAME,
            "${DbHelper.ALIAS_SESSION} = '$hash'",
            null
        )
    }

    fun setAlias(hash: String, alias: String) {
        val map = ContentValues()
        map.put(DbHelper.ALIAS_ALIAS, alias)
        db.update(
            DbHelper.ALIAS_TABLE_NAME,
            map,
            "${DbHelper.ALIAS_SESSION} = '$hash'",
            null
        )
    }
}
