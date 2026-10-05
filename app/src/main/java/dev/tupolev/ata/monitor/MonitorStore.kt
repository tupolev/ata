package dev.tupolev.ata.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrackerConfig(
    val id: String,
    val name: String,
    val armed: Boolean = false,
    val anchorLat: Double? = null,
    val anchorLon: Double? = null,
    val safeRadiusM: Double = 120.0,
    val immediateAlarmM: Double = 300.0,
    val confirmations: Int = 2,
    val intervalMinutes: Int = 5,
    val staleMinutes: Int = 30,
    val suspiciousCount: Int = 0,
    val status: MonitorStatus = MonitorStatus.UNKNOWN,
    val lastTimestamp: Long = 0,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastDistanceM: Double? = null,
)

data class HistoryEntry(
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val distanceM: Double?,
    val status: MonitorStatus,
)

class MonitorStore(context: Context) {

    private val prefs = context.getSharedPreferences("monitor_store", Context.MODE_PRIVATE)

    fun get(id: String): TrackerConfig? =
        prefs.getString("tracker_" + id, null)?.let(::decode)

    fun all(): List<TrackerConfig> =
        prefs.all
            .filterKeys { it.startsWith("tracker_") }
            .values
            .mapNotNull { (it as? String)?.let(::decode) }

    fun armed(): List<TrackerConfig> = all().filter { it.armed }

    fun ensure(id: String, name: String): TrackerConfig {
        return get(id) ?: TrackerConfig(id = id, name = name).also(::save)
    }

    fun save(config: TrackerConfig) {
        prefs.edit().putString("tracker_" + config.id, encode(config)).apply()
    }

    fun appendHistory(id: String, entry: HistoryEntry) {
        val key = "history_" + id
        val previous = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrElse { JSONArray() }
        val out = JSONArray()
        out.put(JSONObject().apply {
            put("timestamp", entry.timestamp)
            put("lat", entry.lat)
            put("lon", entry.lon)
            if (entry.distanceM != null) put("distance", entry.distanceM) else put("distance", JSONObject.NULL)
            put("status", entry.status.name)
        })
        for (i in 0 until minOf(previous.length(), 49)) out.put(previous.get(i))
        prefs.edit().putString(key, out.toString()).apply()
    }

    fun history(id: String): List<HistoryEntry> {
        val arr = runCatching { JSONArray(prefs.getString("history_" + id, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HistoryEntry(
                        timestamp = o.getLong("timestamp"),
                        lat = o.getDouble("lat"),
                        lon = o.getDouble("lon"),
                        distanceM = if (o.isNull("distance")) null else o.getDouble("distance"),
                        status = runCatching { MonitorStatus.valueOf(o.getString("status")) }
                            .getOrDefault(MonitorStatus.UNKNOWN),
                    )
                )
            }
        }
    }

    private fun encode(c: TrackerConfig): String = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("armed", c.armed)
        put("anchorLat", c.anchorLat ?: JSONObject.NULL)
        put("anchorLon", c.anchorLon ?: JSONObject.NULL)
        put("safeRadiusM", c.safeRadiusM)
        put("immediateAlarmM", c.immediateAlarmM)
        put("confirmations", c.confirmations)
        put("intervalMinutes", c.intervalMinutes)
        put("staleMinutes", c.staleMinutes)
        put("suspiciousCount", c.suspiciousCount)
        put("status", c.status.name)
        put("lastTimestamp", c.lastTimestamp)
        put("lastLat", c.lastLat ?: JSONObject.NULL)
        put("lastLon", c.lastLon ?: JSONObject.NULL)
        put("lastDistanceM", c.lastDistanceM ?: JSONObject.NULL)
    }.toString()

    private fun decode(s: String): TrackerConfig {
        val o = JSONObject(s)
        fun d(key: String): Double? = if (o.isNull(key)) null else o.optDouble(key)
        return TrackerConfig(
            id = o.getString("id"),
            name = o.optString("name"),
            armed = o.optBoolean("armed"),
            anchorLat = d("anchorLat"),
            anchorLon = d("anchorLon"),
            safeRadiusM = o.optDouble("safeRadiusM", 120.0),
            immediateAlarmM = o.optDouble("immediateAlarmM", 300.0),
            confirmations = o.optInt("confirmations", 2),
            intervalMinutes = o.optInt("intervalMinutes", 5),
            staleMinutes = o.optInt("staleMinutes", 30),
            suspiciousCount = o.optInt("suspiciousCount"),
            status = runCatching { MonitorStatus.valueOf(o.optString("status", "UNKNOWN")) }
                .getOrDefault(MonitorStatus.UNKNOWN),
            lastTimestamp = o.optLong("lastTimestamp"),
            lastLat = d("lastLat"),
            lastLon = d("lastLon"),
            lastDistanceM = d("lastDistanceM"),
        )
    }
}
