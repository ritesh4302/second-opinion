package org.charged_proton.secondopinion.data.local

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase

/** Creates the process-wide SQLDelight database using app-private Android storage. */
class AndroidDatabaseFactory(private val context: Context) {

    fun create(): SecondOpinionDatabase {
        val driver = AndroidSqliteDriver(
            schema = SecondOpinionDatabase.Schema,
            context = context.applicationContext,
            name = DATABASE_NAME,
        )
        return SecondOpinionDatabase(driver)
    }

    private companion object {
        const val DATABASE_NAME = "second_opinion.db"
    }
}