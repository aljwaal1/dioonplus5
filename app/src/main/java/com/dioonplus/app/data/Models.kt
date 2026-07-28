package com.dioonplus.app.data

/** Core ledger, due-date, and linked-payment models. */
enum class PartyType { CUSTOMER, SUPPLIER }
enum class EntryType { GAVE, TOOK }
enum class DebtStatus { OPEN, PARTIAL, PAID }

data class Party(
    val id: Long,
    val name: String,
    val phone: String,
    val type: PartyType,
    val balanceCents: Long,
    val lastActivityAt: Long?,
)

data class LedgerEntry(
    val id: Long,
    val partyId: Long,
    val type: EntryType,
    val amountCents: Long,
    val note: String,
    val createdAt: Long,
    val dueAt: Long? = null,
    val parentEntryId: Long? = null,
    val paidCents: Long = 0,
) {
    val isPayment: Boolean get() = parentEntryId != null
    val remainingCents: Long get() = if (isPayment) 0 else (amountCents - paidCents).coerceAtLeast(0)
    val debtStatus: DebtStatus
        get() = when {
            isPayment || remainingCents == 0L -> DebtStatus.PAID
            paidCents > 0L -> DebtStatus.PARTIAL
            else -> DebtStatus.OPEN
        }
}

data class DueItem(
    val entryId: Long,
    val partyId: Long,
    val partyName: String,
    val partyPhone: String,
    val entryType: EntryType,
    val originalCents: Long,
    val paidCents: Long,
    val remainingCents: Long,
    val dueAt: Long,
    val note: String,
)

data class DueSummary(
    val overdueCount: Int = 0,
    val todayCount: Int = 0,
    val upcomingCount: Int = 0,
)

data class DashboardSummary(
    val receivableCents: Long = 0,
    val payableCents: Long = 0,
) {
    val netCents: Long get() = receivableCents - payableCents
}

data class ReportRow(
    val entryId: Long,
    val partyId: Long,
    val partyName: String,
    val partyPhone: String,
    val partyType: PartyType,
    val entryType: EntryType,
    val amountCents: Long,
    val note: String,
    val createdAt: Long,
)

data class DailyTotal(
    val dayStartMillis: Long,
    val gaveCents: Long,
    val tookCents: Long,
)
