package com.loantracker.app.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromLocalDate(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun toLocalDate(s: String?): LocalDate? = s?.let { LocalDate.parse(it) }

    @TypeConverter fun fromLocalDateTime(d: LocalDateTime?): String? = d?.toString()
    @TypeConverter fun toLocalDateTime(s: String?): LocalDateTime? = s?.let { LocalDateTime.parse(it) }

    @TypeConverter fun fromInterval(i: PaymentInterval?): String? = i?.name
    @TypeConverter fun toInterval(s: String?): PaymentInterval? = s?.let { PaymentInterval.valueOf(it) }

    @TypeConverter fun fromSource(s: PaymentSource?): String? = s?.name
    @TypeConverter fun toSource(s: String?): PaymentSource? = s?.let { PaymentSource.valueOf(it) }
}
