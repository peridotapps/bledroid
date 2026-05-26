package com.bledroid.ble

import android.content.ContentValues
import android.content.Context
import kotlin.time.Duration.Companion.milliseconds

internal class EncryptedBondedConnectionStore(
    context: Context,
    private val encryptor: ConnectionInfoEncryptor = KeystoreConnectionInfoEncryptor(),
) : BondedConnectionStore {
    private val dbHelper = BondedConnectionDatabaseHelper(context.applicationContext)

    override fun upsert(connection: BondedDeviceConnection) {
        val values = ContentValues().apply {
            put(COLUMN_ADDRESS, connection.address)
            put(COLUMN_AUTO_CONNECT, encryptor.encrypt(connection.autoConnect.toString()))
            put(
                COLUMN_UPDATED_AT,
                encryptor.encrypt(connection.updatedAt.inWholeMilliseconds.toString())
            )
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun findByAddress(address: String): BondedDeviceConnection? {
        val db = dbHelper.readableDatabase
        db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ADDRESS, COLUMN_AUTO_CONNECT, COLUMN_UPDATED_AT),
            "$COLUMN_ADDRESS = ?",
            arrayOf(address),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val encryptedAutoConnect =
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUTO_CONNECT))
            val encryptedUpdatedAt =
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
            val autoConnect =
                encryptor.decrypt(encryptedAutoConnect).toBooleanStrictOrNull() ?: false
            val updatedAt =
                (encryptor.decrypt(encryptedUpdatedAt).toLongOrNull() ?: 0L).milliseconds
            return BondedDeviceConnection(
                address = address,
                autoConnect = autoConnect,
                updatedAt = updatedAt
            )
        }
    }

    override fun deleteByAddress(address: String) {
        dbHelper.writableDatabase.delete(
            TABLE_NAME,
            "$COLUMN_ADDRESS = ?",
            arrayOf(address),
        )
    }

    companion object {
        private const val TABLE_NAME = "bonded_device_connections"
        private const val COLUMN_ADDRESS = "address"
        private const val COLUMN_AUTO_CONNECT = "encrypted_auto_connect"
        private const val COLUMN_UPDATED_AT = "encrypted_updated_at"
    }
}
