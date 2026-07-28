package com.dioonplus.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dioonplus.app.data.*
import com.dioonplus.app.reminder.DueReminderScheduler

class DioonAppState(context: Context) {
    private val database = LedgerDatabase(context.applicationContext)
    private val backupService = BackupService(database)
    private val reminderScheduler = DueReminderScheduler(context.applicationContext)

    var selectedPartyType by mutableStateOf(PartyType.CUSTOMER); private set
    var searchQuery by mutableStateOf(""); private set
    var selectedParty by mutableStateOf<Party?>(null); private set
    var summary by mutableStateOf(DashboardSummary()); private set
    var dueSummary by mutableStateOf(DueSummary()); private set
    var errorMessage by mutableStateOf<String?>(null); private set

    val parties = mutableStateListOf<Party>()
    val entries = mutableStateListOf<LedgerEntry>()
    val reportRows = mutableStateListOf<ReportRow>()
    val dailyTotals = mutableStateListOf<DailyTotal>()
    val dueItems = mutableStateListOf<DueItem>()

    init { refreshAll() }

    fun selectPartyType(type: PartyType) { selectedPartyType = type; refreshParties() }
    fun updateSearch(query: String) { searchQuery = query; refreshParties() }

    fun addParty(name: String, phone: String, type: PartyType): Boolean = execute {
        val id = database.addParty(name, phone, type)
        selectedPartyType = type; searchQuery = ""; refreshAll(); openParty(id)
    }
    fun updateParty(id: Long, name: String, phone: String): Boolean = execute {
        database.updateParty(id, name, phone); refreshAll(true); selectedParty = database.getParty(id)
    }
    fun deleteParty(id: Long): Boolean = execute {
        database.deleteParty(id); selectedParty = null; entries.clear(); refreshAll()
    }
    fun openParty(id: Long) { selectedParty = database.getParty(id); refreshEntries(id) }
    fun closeParty() { selectedParty = null; entries.clear(); refreshAll() }

    fun addEntry(type: EntryType, amountCents: Long, note: String, createdAt: Long, dueAt: Long?): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.addEntry(partyId, type, amountCents, note, createdAt, dueAt); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun updateEntry(entryId: Long, type: EntryType, amountCents: Long, note: String, createdAt: Long, dueAt: Long?): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.updateEntry(entryId, type, amountCents, note, createdAt, dueAt); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun addPayment(parentEntryId: Long, amountCents: Long, note: String, createdAt: Long): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.addPayment(parentEntryId, amountCents, note, createdAt); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun settleInFull(entryId: Long): Boolean {
        val entry = database.getEntry(entryId) ?: return false
        return addPayment(entryId, entry.remainingCents, "سداد كامل", System.currentTimeMillis())
    }

    fun deleteEntry(entryId: Long): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.deleteEntry(entryId); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun exportBackup(): String = backupService.exportJson()
    fun importBackup(json: String): Boolean = execute {
        backupService.importJson(json); selectedParty = null; entries.clear(); searchQuery = ""; refreshAll()
    }
    fun dismissError() { errorMessage = null }

    fun refreshAll(keepSelectedParty: Boolean = false) {
        refreshParties(); summary = database.dashboardSummary()
        reportRows.replaceWith(database.reportRows()); dailyTotals.replaceWith(database.lastDailyTotals())
        dueItems.replaceWith(database.listDueItems())
        val start = startOfToday(); val end = start + 86_400_000L
        dueSummary = DueSummary(
            overdueCount = dueItems.count { it.dueAt < start },
            todayCount = dueItems.count { it.dueAt in start until end },
            upcomingCount = dueItems.count { it.dueAt >= end },
        )
        reminderScheduler.reschedule(dueItems)
        if (keepSelectedParty) selectedParty?.id?.let(::refreshSelectedParty)
    }

    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun refreshParties() { parties.replaceWith(database.listParties(selectedPartyType, searchQuery)) }
    private fun refreshSelectedParty(id: Long) { selectedParty = database.getParty(id); refreshEntries(id) }
    private fun refreshEntries(id: Long) { entries.replaceWith(database.listEntries(id)) }
    private inline fun execute(block: () -> Unit): Boolean = try { errorMessage = null; block(); true } catch (e: Throwable) { errorMessage = e.message ?: "حدث خطأ غير متوقع"; false }
    private fun <T> MutableList<T>.replaceWith(items: List<T>) { clear(); addAll(items) }
}
