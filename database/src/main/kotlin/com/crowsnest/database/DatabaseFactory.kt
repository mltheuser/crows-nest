package com.crowsnest.database

import com.crowsnest.database.repositories.offer.OfferRepository
import com.crowsnest.database.repositories.offer.PostgresOfferRepository
import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    private fun createDatabase(): Database {
        val config = DatabaseConfig.fromEnv()
        return Database.connect(
            url = config.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = config.user,
            password = config.password
        )
    }

    fun createOfferRepository(): OfferRepository = PostgresOfferRepository(createDatabase())
}
