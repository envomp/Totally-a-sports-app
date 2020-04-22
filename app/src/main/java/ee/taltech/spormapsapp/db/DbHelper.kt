package ee.taltech.spormapsapp.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) :
    SQLiteOpenHelper(
        context,
        DATABASE_NAME, null,
        DATABASE_VERSION
    ) {
    companion object {
        const val DATABASE_NAME = "minginimisiiavistjahoktybye.db"
        const val DATABASE_VERSION = 1

        const val CATEGORY_TABLE_NAME = "LOCATIONS"

        const val CATEGORY_ID = "_ID"
        const val CATEGORY_LATITUDE = "LATITUDE"
        const val CATEGORY_LONGITUDE = "LONGITUDE"
        const val CATEGORY_ALTITUDE = "ALTITUDE"
        const val CATEGORY_TIME = "TIME"
        const val CATEGORY_SPEED = "SPEED"
        const val CATEGORY_SESSION = "SESSION"
        const val CATEGORY_TYPE = "TYPE"

        const val ALIAS_TABLE_NAME = "ALIASES"

        const val ALIAS_ID = "_ID"
        const val ALIAS_SESSION = "SESSION"
        const val ALIAS_ALIAS = "ALIAS"
        const val ALIAS_LOCATIONS = "LOCATIONS"
        const val ALIAS_FIRST_DATE = "FIRST_DATE"


        const val SQL_CATEGORY_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS $CATEGORY_TABLE_NAME(" +
                    "$CATEGORY_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$CATEGORY_LATITUDE REAL NOT NULL, " +
                    "$CATEGORY_LONGITUDE REAL NOT NULL, " +
                    "$CATEGORY_ALTITUDE INTEGER NOT NULL, " +
                    "$CATEGORY_TIME TEXT NOT NULL, " +
                    "$CATEGORY_SPEED REAL NOT NULL, " +
                    "$CATEGORY_SESSION TEXT NOT NULL, " +
                    "$CATEGORY_TYPE TEXT NOT NULL" +
                    ");"

        const val SQL_ALIAS_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS $ALIAS_TABLE_NAME(" +
                    "$ALIAS_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$ALIAS_SESSION TEXT NOT NULL, " +
                    "$ALIAS_ALIAS TEXT NOT NULL, " +
                    "$ALIAS_LOCATIONS INTEGER NOT NULL," +
                    "$ALIAS_FIRST_DATE TEXT NOT NULL" +
                    ");"

        const val SQL_DELETE_TABLE_CATEGORY = "DROP TABLE IF EXISTS $CATEGORY_TABLE_NAME"
        const val SQL_DELETE_TABLE_ALIAS = "DROP TABLE IF EXISTS $ALIAS_TABLE_NAME"

    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(SQL_ALIAS_CREATE_TABLE)
        db?.execSQL(SQL_CATEGORY_CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL(SQL_DELETE_TABLE_CATEGORY)
        db?.execSQL(SQL_DELETE_TABLE_ALIAS)
        onCreate(db)
    }
}
