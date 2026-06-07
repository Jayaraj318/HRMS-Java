package com.hrms.dao;

import com.hrms.model.Employee;
import com.hrms.util.DBUtil;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDAO {

    public static List<Employee> list(String search) {
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT * FROM employees";
        if (search != null && !search.trim().isEmpty()) {
            sql += " WHERE full_name LIKE ? OR emp_id LIKE ? OR email LIKE ?";
        }
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (search != null && !search.trim().isEmpty()) {
                String q = "%" + search + "%";
                ps.setString(1, q);
                ps.setString(2, q);
                ps.setString(3, q);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Employee e = new Employee();
                    e.setId(rs.getInt("id"));
                    e.setEmpId(rs.getString("emp_id"));
                    e.setFullName(rs.getString("full_name"));
                    e.setEmail(rs.getString("email"));
                    e.setPhone(rs.getString("phone"));
                    e.setDesignation(rs.getString("designation"));
                    e.setDepartment(rs.getString("department"));
                    Date jd = rs.getDate("joining_date");
                    if (jd != null)
                        e.setJoiningDate(jd.toLocalDate());
                    e.setSalary(rs.getDouble("salary"));
                    e.setPhoto(rs.getString("photo"));
                    list.add(e);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return list;
    }

    public static boolean add(Employee e) throws SQLException {
        String sql = "INSERT INTO employees (emp_id, full_name, email, phone, designation, department, joining_date, salary, photo) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getEmpId());
            ps.setString(2, e.getFullName());
            ps.setString(3, e.getEmail());
            ps.setString(4, e.getPhone());
            ps.setString(5, e.getDesignation());
            ps.setString(6, e.getDepartment());
            if (e.getJoiningDate() != null)
                ps.setDate(7, Date.valueOf(e.getJoiningDate()));
            else
                ps.setNull(7, Types.DATE);
            ps.setDouble(8, e.getSalary());
            ps.setString(9, e.getPhoto());
            return ps.executeUpdate() == 1;
        }
    }

    public static Employee findById(int id) {
        String sql = "SELECT * FROM employees WHERE id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Employee e = new Employee();
                    e.setId(rs.getInt("id"));
                    e.setEmpId(rs.getString("emp_id"));
                    e.setFullName(rs.getString("full_name"));
                    e.setEmail(rs.getString("email"));
                    e.setPhone(rs.getString("phone"));
                    e.setDesignation(rs.getString("designation"));
                    e.setDepartment(rs.getString("department"));
                    Date jd = rs.getDate("joining_date");
                    if (jd != null)
                        e.setJoiningDate(jd.toLocalDate());
                    e.setSalary(rs.getDouble("salary"));
                    e.setPhoto(rs.getString("photo"));
                    return e;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static Employee findByEmpId(String empId) {
        String sql = "SELECT * FROM employees WHERE emp_id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Employee e = new Employee();
                    e.setId(rs.getInt("id"));
                    e.setEmpId(rs.getString("emp_id"));
                    e.setFullName(rs.getString("full_name"));
                    e.setEmail(rs.getString("email"));
                    e.setPhone(rs.getString("phone"));
                    e.setDesignation(rs.getString("designation"));
                    e.setDepartment(rs.getString("department"));
                    Date jd = rs.getDate("joining_date");
                    if (jd != null)
                        e.setJoiningDate(jd.toLocalDate());
                    e.setSalary(rs.getDouble("salary"));
                    e.setPhoto(rs.getString("photo"));
                    return e;
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static boolean update(Employee e) throws SQLException {
        String sql = "UPDATE employees SET emp_id=?, full_name=?, email=?, phone=?, designation=?, department=?, joining_date=?, salary=?, photo=? WHERE id=?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getEmpId());
            ps.setString(2, e.getFullName());
            ps.setString(3, e.getEmail());
            ps.setString(4, e.getPhone());
            ps.setString(5, e.getDesignation());
            ps.setString(6, e.getDepartment());
            if (e.getJoiningDate() != null)
                ps.setDate(7, Date.valueOf(e.getJoiningDate()));
            else
                ps.setNull(7, Types.DATE);
            ps.setDouble(8, e.getSalary());
            ps.setString(9, e.getPhoto());
            ps.setInt(10, e.getId());
            return ps.executeUpdate() == 1;
        }
    }

    public static boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM employees WHERE id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    public static int count() {
        String sql = "SELECT COUNT(*) FROM employees";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return 0;
    }
}
