package com.hrms.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class DBUtil {
    private static Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("src/db.properties")) {
            props.load(fis);
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load DB properties or driver: " + e.getMessage());
        }
    }

   public static Connection getConnection() throws SQLException {

    String host = System.getenv("MYSQLHOST");
    String port = System.getenv("MYSQLPORT");
    String database = System.getenv("MYSQLDATABASE");
    String user = System.getenv("MYSQLUSER");
    String pass = System.getenv("MYSQLPASSWORD");

    if(host != null && port != null && database != null) {

        String url =
            "jdbc:mysql://" + host + ":" + port + "/" + database +
            "?useSSL=false&serverTimezone=UTC";

        return DriverManager.getConnection(url, user, pass);
    }

    String url = props.getProperty("db.url");
    user = props.getProperty("db.user");
    pass = props.getProperty("db.password");

    return DriverManager.getConnection(url, user, pass);
}   
}
