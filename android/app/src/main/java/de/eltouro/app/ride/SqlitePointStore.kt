package de.eltouro.app.ride

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** PointStore on the phone (ride.db). A committed insert survives the process being killed. */
class SqlitePointStore(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "ride.db", null, 1), PointStore {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT, session INTEGER NOT NULL, t INTEGER NOT NULL," +
            " lat REAL NOT NULL, lng REAL NOT NULL, acc INTEGER NOT NULL, kmh REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun add(session: Long, p: TrackPoint) {
        val v = ContentValues()
        v.put("session", session)
        v.put("t", p.t)
        v.put("lat", p.lat)
        v.put("lng", p.lng)
        v.put("acc", p.acc)
        if (p.kmh != null) v.put("kmh", p.kmh) else v.putNull("kmh")
        writableDatabase.insert("points", null, v)
    }

    override fun oldest(limit: Int): List<StoredPoint> {
        val out = ArrayList<StoredPoint>()
        readableDatabase.rawQuery("SELECT id, session, t, lat, lng, acc, kmh FROM points ORDER BY id LIMIT $limit", null).use { c ->
            while (c.moveToNext()) {
                out += StoredPoint(c.getLong(0), c.getLong(1),
                    TrackPoint(c.getLong(2), c.getDouble(3), c.getDouble(4), c.getInt(5), if (c.isNull(6)) null else c.getDouble(6)))
            }
        }
        return out
    }

    override fun delete(session: Long, upToId: Long) {
        writableDatabase.delete("points", "session = ? AND id <= ?", arrayOf(session.toString(), upToId.toString()))
    }

    override fun dropSession(session: Long): Int = writableDatabase.delete("points", "session = ?", arrayOf(session.toString()))

    override fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM points", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    override fun trim(max: Int): Int {
        val n = count() - max
        if (n <= 0) return 0
        writableDatabase.execSQL("DELETE FROM points WHERE id IN (SELECT id FROM points ORDER BY id LIMIT $n)")
        return n
    }
}
