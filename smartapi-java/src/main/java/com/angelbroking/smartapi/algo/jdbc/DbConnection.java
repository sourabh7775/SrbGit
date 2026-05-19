package com.angelbroking.smartapi.algo.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

public class DbConnection {

    private static final Logger log = LoggerFactory.getLogger(DbConnection.class);
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/algo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
    private static final String USER = "root";
    private static final String PASS = "Srbsrb07#";

    private static Connection connection;

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASS);
                log.info("DB connection established");
            }
        } catch (Exception e) {
            log.error("DB connection error", e);
        }
        return connection;
    }
}
