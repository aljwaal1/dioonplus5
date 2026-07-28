package com.dioonplus.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dioonplus.app.data.BackupService
import com.dioonplus.app.data.DailyTotal
import com.dioonplus.app.data.DashboardSummary
import com.dioonplus.app.data.EntryType
import com.dioonplus.app.data.LedgerDatabase
import com.dioonplus.app.data.LedgerEntry
import com.dioonplus.app.data.Party
import com.dioonplus.app.data.PartyType
import com.dioonplus.app.data.ReportRow

class DioonAppState(private val database: LedgerDatabase) {
    private val backupService = BackupService(database)

    var selectedPartyType by mutableStateOf(PartyType.CUSTOMER)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var selectedParty by mutableStateOf<Party?>(null)
        private set
    var summary by mutableStateOf(DashboardSummary())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val parties = mutableStateListOf<Party>()
    val entries = mutableStateListOf<LedgerEntry>()
    val reportRows = mutableStateListOf<ReportRow>()
    val dailyTotals = mutableStateListOf<DailyTotal>()

    init {
        refreshAll()
    }

    fun selectPartyType(type: PartyType) {
        selectedPartyType = type
        refreshParties()
    }

    fun updateSearch(query: String) {
        searchQuery = query
        refreshParties()
    }

    fun addParty(name: String, phone: String, type: PartyType): Boolean = execute {
        val id = database.addParty(name, phone, type)
        selectedPartyType = type
        searchQuery = ""
        refreshAll()
        openParty(id)
    }

    fun updateParty(id: Long, name: String, phone: String): Boolean = execute {
        database.updateParty(id, name, phone)
        refreshAll(keepSelectedParty = true)
        selectedParty = database.getParty(id)
    }

    fun deleteParty(id: Long): Boolean = execute {
        database.deleteParty(id)
        selectedParty = null
        entries.clear()
        refreshAll()
    }

    fun openParty(id: Long) {
        selectedParty = database.getParty(id)
        refreshEntries(id)
    }

    fun closeParty() {
        selectedParty = null
        entries.clear()
        refreshAll()
    }

    fun addEntry(
        type: EntryType,
        amountCents: Long,
        note: String,
        createdAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute {
            database.addEntry(partyId, type, amountCents, note, createdAt)
            refreshSelectedParty(partyId)
            refreshAll(keepSelectedParty = true)
        }
    }

    fun updateEntry(
        entryId: Long,
        type: EntryType,
        amountCents: Long,
        note: String,
        createdAt: Long,
    ): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute {
            database.updateEntry(entryId, type, amountCents, note, createdAt)
            refreshSelectedParty(partyId)
            refreshAll(keepSelectedParty = true)
        }
    }

    fun deleteEntry(entryId: Long): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute {
            database.deleteEntry(entryId)
            refreshSelectedParty(partyId)
            refreshAll(keepSelectedParty = true)
        }
    }

    fun exportBackup(): String = backupService.exportJson()

    fun importBackup(json: String): Boolean = execute {
        backupService.importJson(json)
        selectedParty = null
        entries.clear()
        searchQuery = ""
        refreshAll()
    }

    fun dismissError() {
        errorMessage = null
    }

    fun refreshAll(keepSelectedParty: Boolean = false) {
        refreshParties()
        summary = database.dashboardSummary()
        reportRows.replaceWith(database.reportRows())
        dailyTotals.replaceWith(database.lastDailyTotals())
        if (keepSelectedParty) {
            selectedParty?.id?.let(::refreshSelectedParty)
        }
    }

    private fun refreshParties() {
        parties.replaceWith(database.listParties(selectedPartyType, searchQuery))
    }

    private fun refreshSelectedParty(partyId: Long) {
        selectedParty = database.getParty(partyId)
        refreshEntries(partyId)
    }

    private fun refreshEntries(partyId: Long) {
        entries.replaceWith(database.listEntries(partyId))
    }

    private inline fun execute(block: () -> Unit): Boolean = try {
        errorMessage = null
        block()
        true
    } catch (error: Throwable) {
        errorMessage = error.message ?: "حدث خطأ غير متوقع"
        false
    }

    private fun <T> MutableList<T>.replaceWith(newItems: List<T>) {
        clear()
        addAll(newItems)
    }
}
