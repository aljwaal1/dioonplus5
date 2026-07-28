package com.dioonplus.app.data

import java.util.Calendar

enum class DueStatus { OVERDUE, TODAY, UPCOMING, SETTLED }

data class DueItem(
    val entryId: Long,
    val partyId: Long,
    val partyName: String,
    val partyPhone: String,
    val entryType: EntryType,
    val originalCents: Long,
    val paidCents: Long,
    val dueAt: Long,
    val settledAt: Long?,
    val note: String,
) {
    val remainingCents: Long get() = (originalCents - paidCents).coerceAtLeast(0)

    fun status(now: Long = System.currentTimeMillis()): DueStatus {
        if (remainingCents == 0L || settledAt != null) return DueStatus.SETTLED
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return when {
            dueAt < todayStart -> DueStatus.OVERDUE
            dueAt < tomorrowStart -> DueStatus.TODAY
            else -> DueStatus.UPCOMING
        }
    }
}
