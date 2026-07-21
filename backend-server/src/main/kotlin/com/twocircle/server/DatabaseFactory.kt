package com.twocircle.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

/**
 * PostgreSQL connection pool + Exposed ORM bootstrap.
 *
 * Uses HikariCP for connection pooling. Database schema is auto-created on startup
 * (fine for development; production should use migration tooling like Flyway).
 */
object DatabaseFactory {

    fun init() {
        val dataSource = hikariDataSource()
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(Users, Rides)
        }
    }

    private fun hikariDataSource(): DataSource {
        val config = HikariConfig().apply {
            driverClassName = System.getenv("DB_DRIVER") ?: "org.postgresql.Driver"
            jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/twocircle"
            username = System.getenv("DB_USER") ?: "twocircle"
            password = System.getenv("DB_PASSWORD") ?: "twocircle_dev"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        return HikariDataSource(config)
    }

    /** Run a DB query on the IO dispatcher (suspended). */
    suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }
}
