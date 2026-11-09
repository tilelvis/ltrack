package com.loantracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [Loan::class, Payment::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao
    abstract fun paymentDao(): PaymentDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loan_tracker.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}

class LoanRepository(private val db: AppDatabase) {
    fun observeLoans(): Flow<List<Loan>> = db.loanDao().observeAll()
    fun observeAllLoansIncludingHidden(): Flow<List<Loan>> = db.loanDao().observeAllIncludingHidden()
    fun observeLoan(id: String): Flow<Loan?> = db.loanDao().observeById(id)
    fun observePayments(loanId: String): Flow<List<Payment>> = db.paymentDao().observeByLoan(loanId)
    fun observeAllPayments(): Flow<List<Payment>> = db.paymentDao().observeAll()
    fun observeTotalPaid(loanId: String): Flow<Double> = db.paymentDao().observeTotalPaid(loanId)
    fun observeTotalPaidAll(): Flow<Double> = db.paymentDao().observeTotalPaidAll()
    fun observeTodayPayment(loanId: String): Flow<Payment?> = db.paymentDao().observeTodayPayment(loanId)

    suspend fun upsertLoan(loan: Loan) = db.loanDao().upsert(loan)
    suspend fun deleteLoan(loan: Loan) = db.loanDao().delete(loan)
    suspend fun setLoanHidden(id: String, hidden: Boolean) = db.loanDao().setHidden(id, hidden)
    suspend fun addPayment(payment: Payment) = db.paymentDao().insert(payment)
    suspend fun deletePayment(payment: Payment) = db.paymentDao().delete(payment)
    suspend fun findByCode(code: String): Payment? = db.paymentDao().findByCode(code)
    suspend fun getLoan(id: String): Loan? = db.loanDao().getById(id)
    suspend fun totalPaidOnce(loanId: String): Double = db.paymentDao().sumForLoan(loanId)
    suspend fun countCompleted(): Int = db.loanDao().countCompleted()
}
