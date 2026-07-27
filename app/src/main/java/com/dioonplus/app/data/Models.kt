package com.dioonplus.app.data

enum class PartyType { CUSTOMER, SUPPLIER }
enum class EntryType { GAVE, TOOK }

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
)

data class DashboardSummary(
    val receivableCents: Long = 0,
    val payableCents: Long = 0,
) {
    val netCents: Long get() = receivableCents - payableCents
}

data class ReportRow(
    val entryId: Long,
    val partyName: String,
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
