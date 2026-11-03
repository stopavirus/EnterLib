package app.enterlib.core

import java.net.URI

object UrlValidator {
    /** Only http(s) URLs with a host and no embedded credentials are accepted. */
    fun isSafeHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val u = URI(url.trim())
            (u.scheme.equals("http", true) || u.scheme.equals("https", true)) &&
                !u.host.isNullOrBlank() && u.userInfo == null
        } catch (e: Exception) {
            false
        }
    }
}
