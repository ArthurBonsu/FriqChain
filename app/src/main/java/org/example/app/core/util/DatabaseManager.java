package org.example.app.core.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DB_URL = "jdbc:sqlite:blockchain.db";
    private static Connection connection;

    static {
        try {
            // Ensure SQLite JDBC driver is loaded
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "SQLite JDBC Driver not found", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(false);
        }
        return connection;
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Create crypto tables
            stmt.execute("CREATE TABLE IF NOT EXISTS keypairs (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         "address TEXT NOT NULL UNIQUE," +
                         "public_key BLOB NOT NULL," +
                         "private_key BLOB," +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS certificates (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         "subject TEXT NOT NULL," +
                         "issuer TEXT NOT NULL," +
                         "cert_data BLOB NOT NULL," +
                         "valid_from TIMESTAMP," +
                         "valid_to TIMESTAMP," +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            conn.commit();
            LOGGER.info("Database initialized successfully");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database", e);
        }
    }
    
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database connection", e);
            }
        }
    }
}