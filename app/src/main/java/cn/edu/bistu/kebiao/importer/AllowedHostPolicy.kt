package cn.edu.bistu.kebiao.importer

import java.net.URI

object AllowedHostPolicy {
    private val allowedHosts = setOf(
        "jwxt.bistu.edu.cn",
        "wxjw.bistu.edu.cn",
    )

    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host?.lowercase() in allowedHosts
        }.getOrDefault(false)
    }

    fun isTeachingSystemPage(url: String?): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("jwxt.bistu.edu.cn", ignoreCase = true)
    }.getOrDefault(false)
}
