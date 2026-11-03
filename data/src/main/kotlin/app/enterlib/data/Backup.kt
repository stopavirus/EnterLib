package app.enterlib.data

import org.json.JSONArray
import org.json.JSONObject

/** Local backup of watchlist and progress, keyed by match key so it survives a library re-sync. */
class BackupManager(private val dao: LibraryDao) {
    suspend fun export(): String {
        val wl = JSONArray()
        for (w in dao.allWatch()) {
            val c = dao.getContent(w.contentId) ?: continue
            wl.put(JSONObject().put("key", c.matchKey).put("fav", w.favourite).put("at", w.addedAt))
        }
        val pr = JSONArray()
        for (p in dao.allProgress()) {
            val c = dao.getContent(p.contentId) ?: continue
            val ep = if (p.episodeId == 0L) null else dao.getEpisodes(c.id).firstOrNull { it.id == p.episodeId } ?: continue
            pr.put(JSONObject().put("key", c.matchKey).put("season", ep?.season ?: -1).put("episode", ep?.episode ?: -1)
                .put("pos", p.positionMs).put("dur", p.durationMs).put("done", p.completed).put("at", p.updatedAt))
        }
        return JSONObject().put("version", 1).put("watchlist", wl).put("progress", pr).toString()
    }

    /** Returns the number of entries restored; entries whose title is not in the library yet are skipped. */
    suspend fun import(text: String): Int {
        val o = JSONObject(text)
        require(o.optInt("version") == 1) { "Unsupported backup version" }
        var n = 0
        val wl = o.optJSONArray("watchlist") ?: JSONArray()
        for (i in 0 until wl.length()) {
            val w = wl.getJSONObject(i)
            val c = dao.getByKey(w.getString("key")) ?: continue
            dao.upsertWatch(WatchlistEntity(c.id, w.optBoolean("fav"), w.optLong("at")))
            n++
        }
        val pr = o.optJSONArray("progress") ?: JSONArray()
        for (i in 0 until pr.length()) {
            val p = pr.getJSONObject(i)
            val c = dao.getByKey(p.getString("key")) ?: continue
            val eid = if (p.getInt("season") < 0) 0L else dao.findEpisode(c.id, p.getInt("season"), p.getInt("episode"))?.id ?: continue
            dao.upsertProgress(ProgressEntity(c.id, eid, p.getLong("pos"), p.getLong("dur"), p.optLong("at"), p.optBoolean("done")))
            n++
        }
        return n
    }
}
