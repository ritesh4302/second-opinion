package org.charged_proton.secondopinion.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.charged_proton.secondopinion.data.local.db.SecondOpinionDatabase

/** Creates the process-wide SQLDelight database using app-private iOS storage. */
class IosDatabaseFactory {

    fun create(): SecondOpinionDatabase {
        val driver = NativeSqliteDriver(
            schema = SecondOpinionDatabase.Schema,
            name = DATABASE_NAME,
        )
        return SecondOpinionDatabase(driver)
    }

    private companion object {
        const val DATABASE_NAME = "second_opinion.db"
    }
}
