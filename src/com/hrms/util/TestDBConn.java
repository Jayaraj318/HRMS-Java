package com.hrms.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TestDBConn {
    public static void main(String[] args) {
        String sql = "SELECT emp_id, full_name, department, designation FROM employees";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            // Keep the original connection success message format
            if (c != null && !c.isClosed()) {
                System.out.println("Connection successful: " + c.getMetaData().getURL());
            } else {
                System.out.println("Connection failed (null or closed).");
                return;
            }

            System.out.println(); // blank line
            while (rs.next()) {
                String empId = rs.getString("emp_id");
                String name = rs.getString("full_name");
                String dept = rs.getString("department");
                String desig = rs.getString("designation");
                System.out.println(empId + " | " + name + " | " + (dept == null ? "" : dept) + " | " + (desig == null ? "" : desig));
            }

        } catch (Exception e) {
            System.err.println("DB query error:");
            e.printStackTrace();
        }
    }
}