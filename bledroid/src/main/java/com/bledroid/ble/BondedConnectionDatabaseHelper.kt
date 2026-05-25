package com.bledroid.ble

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class BondedConnectionDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bonded_device_connections (
                address TEXT PRIMARY KEY,
                encrypted_auto_connect TEXT NOT NULL,
                encrypted_updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != newVersion) {
            db.execSQL("DROP TABLE IF EXISTS bonded_device_connections")
            onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_NAME = "bledroid_connections.db"
        private const val DATABASE_VERSION = 1
    }
}
