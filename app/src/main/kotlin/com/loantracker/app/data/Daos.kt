package com.loantracker.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE isHidden = 0 ORDER BY dueDate ASC")
    fun observeAll(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY dueDate ASC")
    fun observeAllIncludingHidden(): Flow<List<Loan>>

    @Query("SELECT * FROM loans WHERE id = :id")
    fun observeById(id: String): Flow<Loan?>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: String): Loan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(loan: Loan)

    @Delete
    suspend fun delete(loan: Loan)

    @Query("UPDATE loans SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE loanId = :loanId ORDER BY paidAt DESC")
    fun observeByLoan(loanId: String): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun observeAll(): Flow<List<Payment>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE loanId = :loanId")
    fun observeTotalPaid(loanId: String): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments")
    fun observeTotalPaidAll(): Flow<Double>

    @Query("SELECT * FROM payments WHERE mpesaCode = :code LIMIT 1")
    suspend fun findByCode(code: String): Payment?

    @Query("SELECT * FROM payments WHERE loanId = :loanId AND date(paidAt) = date('now') LIMIT 1")
    fun observeTodayPayment(loanId: String): Flow<Payment?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: Payment)

    @Delete
    suspend fun delete(payment: Payment)
}
