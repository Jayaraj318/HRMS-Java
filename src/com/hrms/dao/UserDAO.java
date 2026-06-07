package com.hrms.dao;

import com.hrms.model.User;
import com.hrms.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.MessageDigest;

public class UserDAO {

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static User authenticate(String username, String password) {
String sql = "SELECT id, username, password, role, employee_id FROM users WHERE username = ?";        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String dbPass = rs.getString("password");
                    String hash = sha256Hex(password);
                    if (dbPass != null && dbPass.equalsIgnoreCase(hash)) {
                        Integer empId = null;
                        int e = rs.getInt("employee_id"); if (!rs.wasNull()) empId = e;
                        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"), empId);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
