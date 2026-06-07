package com.hrms.dao;

import com.hrms.model.Department;
import com.hrms.util.DBUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DepartmentDAO {

    public static List<Department> list() {
        List<Department> list = new ArrayList<>();
        String sql = "SELECT * FROM departments ORDER BY department_name";
        try (Connection c = DBUtil.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Department d = new Department();
                d.setId(rs.getInt("id"));
                d.setDepartmentName(rs.getString("department_name"));
                d.setManagerName(rs.getString("manager_name"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null)
                    d.setCreatedAt(ts.toLocalDateTime());
                list.add(d);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public static Department findById(int id) {
        String sql = "SELECT * FROM departments WHERE id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Department d = new Department();
                    d.setId(rs.getInt("id"));
                    d.setDepartmentName(rs.getString("department_name"));
                    d.setManagerName(rs.getString("manager_name"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null)
                        d.setCreatedAt(ts.toLocalDateTime());
                    return d;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static boolean add(Department d) throws SQLException {
        String sql = "INSERT INTO departments (department_name, manager_name) VALUES (?,?)";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d.getDepartmentName());
            ps.setString(2, d.getManagerName());
            return ps.executeUpdate() == 1;
        }
    }

    public static boolean update(Department d) throws SQLException {
        String sql = "UPDATE departments SET department_name=?, manager_name=? WHERE id=?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d.getDepartmentName());
            ps.setString(2, d.getManagerName());
            ps.setInt(3, d.getId());
            return ps.executeUpdate() == 1;
        }
    }

    public static boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM departments WHERE id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    public static int count() {
        String sql = "SELECT COUNT(*) FROM departments";
        try (Connection c = DBUtil.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}
