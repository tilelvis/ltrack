package com.loantracker.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.loantracker.app.data.AppDatabase
import com.loantracker.app.data.DailyPaymentCalculator
import com.loantracker.app.data.DailyPaymentRecommendation
import com.loantracker.app.data.Loan
import com.loantracker.app.data.LoanRepository
import com.loantracker.app.data.Payment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LoanViewModel(private val repo: LoanRepository) : ViewModel() {

    val loans: StateFlow<List<Loan>> = repo.observeLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLoans: StateFlow<List<Loan>> = repo.observeAllLoansIncludingHidden()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<Payment>> = repo.observeAllPayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPaidAll: StateFlow<Double> = repo.observeTotalPaidAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // CACHED per-loan flows — prevents recomposition loops.
    // Before this cache, every recomposition called loan(id) which created a
    // NEW StateFlow, collectAsState subscribed to it, triggered another
    // recomposition, which created another new flow → infinite loop →
    // "vibrating" UI. Now the same StateFlow instance is returned every time.
    private val loanCache = ConcurrentHashMap<String, StateFlow<Loan?>>()
    private val paymentsCache = ConcurrentHashMap<String, StateFlow<List<Payment>>>()
    private val totalPaidCache = ConcurrentHashMap<String, StateFlow<Double>>()
    private val todayPaymentCache = ConcurrentHashMap<String, StateFlow<Payment?>>()
    private val recommendationCache = ConcurrentHashMap<String, StateFlow<DailyPaymentRecommendation?>>()

    fun loan(id: String): StateFlow<Loan?> = loanCache.getOrPut(id) {
        repo.observeLoan(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun payments(loanId: String): StateFlow<List<Payment>> = paymentsCache.getOrPut(loanId) {
        repo.observePayments(loanId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun totalPaid(loanId: String): StateFlow<Double> = totalPaidCache.getOrPut(loanId) {
        repo.observeTotalPaid(loanId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    }

    fun todayPayment(loanId: String): StateFlow<Payment?> = todayPaymentCache.getOrPut(loanId) {
        repo.observeTodayPayment(loanId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun dailyRecommendation(loanId: String): StateFlow<DailyPaymentRecommendation?> =
        recommendationCache.getOrPut(loanId) {
            combine(loan(loanId), totalPaid(loanId)) { l, paid ->
                l?.let { DailyPaymentCalculator.calculate(it, paid) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        }

    fun saveLoan(loan: Loan) = viewModelScope.launch { repo.upsertLoan(loan) }
    fun deleteLoan(loan: Loan) = viewModelScope.launch { repo.deleteLoan(loan) }
    fun hideLoan(id: String, hidden: Boolean) = viewModelScope.launch { repo.setLoanHidden(id, hidden) }
    fun addPayment(payment: Payment) = viewModelScope.launch { repo.addPayment(payment) }
    fun deletePayment(payment: Payment) = viewModelScope.launch { repo.deletePayment(payment) }

    suspend fun isCodeImported(code: String): Boolean = repo.findByCode(code.uppercase()) != null

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = LoanRepository(AppDatabase.get(context.applicationContext))
                return LoanViewModel(repo) as T
            }
        }
        fun newId(): String = UUID.randomUUID().toString()
    }
}
