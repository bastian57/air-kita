package com.daftech.airkita.data

import androidx.room.Embedded
import androidx.room.Relation
import com.daftech.airkita.data.entity.Bill
import com.daftech.airkita.data.entity.Customer

data class BillWithCustomer(
    @Embedded val bill: Bill,
    @Relation(parentColumn = "customerId", entityColumn = "id", entity = Customer::class)
    val customer: Customer
)
