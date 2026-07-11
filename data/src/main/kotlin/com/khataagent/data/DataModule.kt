package com.khataagent.data

import android.content.Context
import com.khataagent.core.agent.StateBlockBuilder
import com.khataagent.core.data.LedgerRepository

/**
 * Manual factories — no Hilt/DI framework in this hackathon build. Phase-2 integration wires
 * these into the app; nothing here touches MainActivity or DI config.
 */

fun provideDatabase(context: Context): KhataDatabase = KhataDatabase.build(context)

fun provideLedgerRepository(context: Context): LedgerRepository {
    val db = provideDatabase(context)
    return RoomLedgerRepository(
        customerDao = db.customerDao(),
        transactionDao = db.transactionDao(),
        inventoryDao = db.inventoryDao(),
        deferralDao = db.deferralDao(),
    )
}

fun provideStateBlockBuilder(repository: LedgerRepository): StateBlockBuilder =
    StateBlockBuilderImpl(repository)
