package app.enterlib.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpStatusException(val code: Int, message: String) : IOException(message)

class Http(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
) {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, "HTTP ${r.code} from ${r.request.url.host}")
            r.body?.string() ?: ""
        }
    }
}

internal val json = Json { ignoreUnknownKeys = true }
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.arr(k: String): JsonArray = (this[k] as? JsonArray) ?: JsonArray(emptyList())
internal fun JsonObject.sub(k: String): JsonObject? = this[k] as? JsonObject
internal fun parse(body: String): JsonObject =
    json.parseToJsonElement(body) as? JsonObject ?: throw IOException("Unexpected response")
