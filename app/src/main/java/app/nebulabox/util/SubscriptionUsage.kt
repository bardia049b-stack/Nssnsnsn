package app.nebulabox.util

import android.content.Context
import app.nebulabox.R
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.locale.LocaleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SubscriptionUsage {

    private const val HEADER = "subscription-userinfo"

    data class Quota(
        val upload: Long = 0L,
        val download: Long = 0L,
        val total: Long = 0L,
        val expire: Long = 0L,
    ) {
        val used: Long get() = upload + download
    }

    fun parse(headers: Map<String, List<String>>): Quota? {
        val raw = headers.entries
            .firstOrNull { it.key.equals(HEADER, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.trim()
            ?: return null
        if (raw.isEmpty()) return null

        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        var found = false

        raw.split(';', ',').forEach { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@forEach
            val key = pair.substring(0, index).trim().lowercase(Locale.US)
            val value = pair.substring(index + 1).trim().toLongOrNull() ?: return@forEach
            when (key) {
                "upload" -> upload = value
                "download" -> download = value
                "total" -> total = value
                "expire" -> expire = value
            }
            found = true
        }

        return if (found) Quota(upload, download, total, expire) else null
    }

    fun applyTo(item: SubscriptionItem, quota: Quota?): SubscriptionItem {
        if (quota == null) return item
        return item.copy(
            uploadBytes = quota.upload,
            downloadBytes = quota.download,
            totalBytes = quota.total,
            expireAtSeconds = quota.expire,
        )
    }

    fun amount(bytes: Long): String = if (bytes <= 0L) "0 B" else Formatters.size(bytes)

    fun usageLine(context: Context, item: SubscriptionItem): String = when {
        item.hasQuota -> context.getString(R.string.subscription_usage, amount(item.usedBytes), amount(item.totalBytes))
        item.usedBytes > 0L -> context.getString(R.string.subscription_used, amount(item.usedBytes))
        else -> ""
    }

    fun remainingLine(context: Context, item: SubscriptionItem): String {
        if (!item.hasQuota) return ""
        val left = (item.totalBytes - item.usedBytes).coerceAtLeast(0L)
        return context.getString(R.string.subscription_remaining, amount(left))
    }

    fun expiryLine(context: Context, item: SubscriptionItem): String {
        if (item.expireAtSeconds <= 0L) return ""
        val days = item.daysLeft
        val date = formatDate(context, item.expireAtSeconds)
        return when {
            days < 0 -> context.getString(R.string.subscription_expired_on, date)
            days == 0 -> context.getString(R.string.subscription_expires_today)
            days == 1 -> context.getString(R.string.subscription_day_left, 1)
            days < 30 -> context.getString(R.string.subscription_days_left, days)
            else -> context.getString(R.string.subscription_expires_on, date)
        }
    }

    fun progressLabel(item: SubscriptionItem): String {
        if (!item.hasQuota) return ""
        return "${(item.quotaFraction * 100f).toInt()}%"
    }

    private fun formatDate(context: Context, seconds: Long): String {
        val millis = seconds * 1000L
        if (LocaleManager.isPersian(context)) {
            val formatted = runCatching {
                val locale = Locale.forLanguageTag("fa-IR-u-ca-persian")
                val format = android.icu.text.SimpleDateFormat("yyyy/MM/dd", locale)
                format.timeZone = android.icu.util.TimeZone.getDefault()
                format.format(Date(millis))
            }.getOrNull()
            if (!formatted.isNullOrBlank()) return formatted
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
    }

    fun statusOf(item: SubscriptionItem): State = when {
        item.isExpired -> State.EXPIRED
        item.isQuotaExhausted -> State.EXHAUSTED
        item.hasQuota && item.quotaFraction >= 0.85f -> State.LOW
        item.hasQuota || item.expireAtSeconds > 0L -> State.OK
        else -> State.UNKNOWN
    }

    enum class State { OK, LOW, EXHAUSTED, EXPIRED, UNKNOWN }
}
