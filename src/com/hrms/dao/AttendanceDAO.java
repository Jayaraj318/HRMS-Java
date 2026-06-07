package com.hrms.dao;

import com.hrms.model.Attendance;
import com.hrms.util.DBUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AttendanceDAO {

    public static List<Attendance> listByDate(LocalDate date) {
        List<Attendance> list = new ArrayList<>();
        String sql = "SELECT a.*, e.full_name FROM attendance a LEFT JOIN employees e ON a.employee_id=e.id WHERE a.attendance_date = ? ORDER BY e.full_name";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Attendance a = new Attendance();
                    a.setId(rs.getInt("id"));
                    a.setEmployeeId(rs.getInt("employee_id"));
                    a.setEmployeeName(rs.getString("full_name"));
                    Date ad = rs.getDate("attendance_date"); if (ad != null) a.setAttendanceDate(ad.toLocalDate());
                    Timestamp ci = rs.getTimestamp("check_in"); if (ci != null) a.setCheckIn(ci.toLocalDateTime());
                    Timestamp co = rs.getTimestamp("check_out"); if (co != null) a.setCheckOut(co.toLocalDateTime());
                    a.setStatus(rs.getString("status"));
                    list.add(a);
                }
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return list;
    }

    public static Attendance findByEmployeeAndDate(int employeeId, LocalDate date) {
        String sql = "SELECT a.*, e.full_name FROM attendance a LEFT JOIN employees e ON a.employee_id=e.id WHERE a.employee_id=? AND a.attendance_date=?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId); ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Attendance a = new Attendance();
                    a.setId(rs.getInt("id"));
                    a.setEmployeeId(rs.getInt("employee_id"));
                    a.setEmployeeName(rs.getString("full_name"));
                    Date ad = rs.getDate("attendance_date"); if (ad != null) a.setAttendanceDate(ad.toLocalDate());
                    Timestamp ci = rs.getTimestamp("check_in"); if (ci != null) a.setCheckIn(ci.toLocalDateTime());
                    Timestamp co = rs.getTimestamp("check_out"); if (co != null) a.setCheckOut(co.toLocalDateTime());
                    a.setStatus(rs.getString("status"));
                    return a;
                }
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return null;
    }

    public static boolean checkIn(int employeeId, LocalDateTime when) throws SQLException {
        LocalDate date = when.toLocalDate();
        Attendance existing = findByEmployeeAndDate(employeeId, date);
        if (existing == null) {
            String sql = "INSERT INTO attendance (employee_id, attendance_date, check_in, status) VALUES (?,?,?, 'PRESENT')";
            try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, employeeId);
                ps.setDate(2, Date.valueOf(date));
                ps.setTimestamp(3, Timestamp.valueOf(when));
                return ps.executeUpdate() == 1;
            }
        } else {
            String sql = "UPDATE attendance SET check_in=? WHERE id=?";
            try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.valueOf(when));
                ps.setInt(2, existing.getId());
                return ps.executeUpdate() == 1;
            }
        }
    }

    public static boolean checkOut(int employeeId, LocalDateTime when) throws SQLException {
        LocalDate date = when.toLocalDate();
        Attendance existing = findByEmployeeAndDate(employeeId, date);
        if (existing == null) {
            // create record with check_out only
            String sql = "INSERT INTO attendance (employee_id, attendance_date, check_out, status) VALUES (?,?,?, 'PRESENT')";
            try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, employeeId);
                ps.setDate(2, Date.valueOf(date));
                ps.setTimestamp(3, Timestamp.valueOf(when));
                return ps.executeUpdate() == 1;
            }
        } else {
            String sql = "UPDATE attendance SET check_out=? WHERE id=?";
            try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.valueOf(when));
                ps.setInt(2, existing.getId());
                return ps.executeUpdate() == 1;
            }
        }
    }

    public static int countPresent(LocalDate date) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE attendance_date = ? AND status='PRESENT'";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date)); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return 0;
    }

    public static int countAbsent(LocalDate date) {
        String sql = "SELECT COUNT(*) FROM attendance WHERE attendance_date = ? AND status='ABSENT'";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return 0;
    }

    public static List<Attendance> listByEmployeeAndDate(int employeeId, LocalDate date) {
        List<Attendance> list = new ArrayList<>();
        Attendance a = findByEmployeeAndDate(employeeId, date);
        if (a != null) list.add(a);
        return list;
    }
}
