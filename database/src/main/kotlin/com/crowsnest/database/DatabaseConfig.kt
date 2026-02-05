package com.crowsnest.database

data class DatabaseConfig(val jdbcUrl: String, val user: String, val password: String) {
    companion object {
        fun fromEnv() = DatabaseConfig(
            jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/crowsnest",
            user = System.getenv("DATABASE_USER") ?: "dev",
            password = System.getenv("DATABASE_PASSWORD") ?: "dev"
        )
    }
}
