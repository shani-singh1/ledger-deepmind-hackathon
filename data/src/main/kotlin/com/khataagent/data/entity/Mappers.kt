package com.khataagent.data.entity

import com.khataagent.core.model.Customer
import com.khataagent.core.model.DeferralEntry
import com.khataagent.core.model.InventoryItem
import com.khataagent.core.model.Transaction
import com.khataagent.core.model.TxnSource
import com.khataagent.core.model.TxnStatus
import com.khataagent.core.model.TxnType

/** Entity <-> :core domain model conversions. Keeps Room out of every other module. */

fun CustomerEntity.toDomain(): Customer = Customer(
    id = id,
    name = name,
    phoneHint = phoneHint,
    namePhonetic = namePhonetic,
    createdAt = createdAt,
)

fun Customer.toEntity(): CustomerEntity = CustomerEntity(
    id = id,
    name = name,
    phoneHint = phoneHint,
    namePhonetic = namePhonetic,
    createdAt = createdAt,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    customerId = customerId,
    customerName = customerName,
    type = TxnType.valueOf(type),
    amount = amount,
    item = item,
    note = note,
    status = TxnStatus.valueOf(status),
    source = TxnSource.valueOf(source),
    createdAt = createdAt,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    customerId = customerId,
    customerName = customerName,
    type = type.name,
    amount = amount,
    item = item,
    note = note,
    status = status.name,
    source = source.name,
    createdAt = createdAt,
)

fun InventoryEntity.toDomain(): InventoryItem = InventoryItem(
    id = id,
    item = item,
    qty = qty,
    unit = unit,
    lowWatermark = lowWatermark,
)

fun InventoryItem.toEntity(): InventoryEntity = InventoryEntity(
    id = id,
    item = item,
    qty = qty,
    unit = unit,
    lowWatermark = lowWatermark,
)

fun DeferralEntity.toDomain(): DeferralEntry = DeferralEntry(
    id = id,
    turnId = turnId,
    rawModelOutput = rawModelOutput,
    reason = reason,
    resolution = resolution,
    createdAt = createdAt,
)

fun DeferralEntry.toEntity(): DeferralEntity = DeferralEntity(
    id = id,
    turnId = turnId,
    rawModelOutput = rawModelOutput,
    reason = reason,
    resolution = resolution,
    createdAt = createdAt,
)
