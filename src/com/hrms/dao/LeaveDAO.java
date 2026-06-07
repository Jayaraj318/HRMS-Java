package com.hrms.dao;

import com.hrms.model.LeaveRequest;
import com.hrms.util.DBUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class LeaveDAO {

    public static boolean apply(LeaveRequest lr) throws SQLException {
        String sql = "INSERT INTO leave_requests (employee_id, leave_type, from_date, to_date, total_days, reason, status, approved_by, created_at) VALUES (?,?,?,?,?,?,?,?,NOW())";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lr.getEmployeeId());
            ps.setString(2, lr.getLeaveType());
            ps.setDate(3, Date.valueOf(lr.getFromDate()));
            ps.setDate(4, Date.valueOf(lr.getToDate()));
            ps.setInt(5, lr.getTotalDays());
            ps.setString(6, lr.getReason());
            ps.setString(7, lr.getStatus() == null ? "PENDING" : lr.getStatus());
            if (lr.getApprovedBy() != null)
                ps.setString(8, lr.getApprovedBy());
            else
                ps.setNull(8, Types.VARCHAR);
            return ps.executeUpdate() == 1;
        }
    }

    public static List<LeaveRequest> listAll(String search, String status) {
        List<LeaveRequest> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT lr.*, e.full_name FROM leave_requests lr JOIN employees e ON lr.employee_id = e.id");
        boolean whereAdded = false;
        if (status != null && !status.trim().isEmpty()) {
            sb.append(" WHERE lr.status = ?"); whereAdded = true;
        }
        if (search != null && !search.trim().isEmpty()) {
            if (whereAdded) sb.append(" AND "); else sb.append(" WHERE ");
            sb.append(" e.full_name LIKE ?");
        }
        sb.append(" ORDER BY lr.created_at DESC");
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int idx = 1;
            if (status != null && !status.trim().isEmpty()) ps.setString(idx++, status);
            if (search != null && !search.trim().isEmpty()) ps.setString(idx++, "%" + search + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LeaveRequest lr = mapRow(rs);
                    list.add(lr);
                }
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return list;
    }

    public static LeaveRequest findById(int id) {
        String sql = "SELECT lr.*, e.full_name FROM leave_requests lr JOIN employees e ON lr.employee_id = e.id WHERE lr.id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return null;
    }

    public static boolean approve(int id, String approver) throws SQLException {
        String sql = "UPDATE leave_requests SET status='APPROVED', approved_by=? WHERE id=? AND status='PENDING'";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, approver);
            ps.setInt(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    public static boolean reject(int id, String approver) throws SQLException {
        String sql = "UPDATE leave_requests SET status='REJECTED', approved_by=? WHERE id=? AND status='PENDING'";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, approver);
            ps.setInt(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    public static int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM leave_requests WHERE status = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return 0;
    }

    public static Integer getEmployeeIdForUser(int userId) {
        String sql = "SELECT employee_id FROM users WHERE id = ?";
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { int v = rs.getInt(1); if (rs.wasNull()) return null; return v; } }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return null;
    }

    public static List<LeaveRequest> listByEmployee(int employeeId, String status) {
        List<LeaveRequest> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT lr.*, e.full_name FROM leave_requests lr JOIN employees e ON lr.employee_id = e.id WHERE lr.employee_id = ?");
        if (status != null && !status.trim().isEmpty()) sb.append(" AND lr.status = ?");
        sb.append(" ORDER BY lr.created_at DESC");
        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sb.toString())) {
            ps.setInt(1, employeeId);
            if (status != null && !status.trim().isEmpty()) ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException ex) { ex.printStackTrace(); }
        return list;
    }

    private static LeaveRequest mapRow(ResultSet rs) throws SQLException {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(rs.getInt("id"));
        lr.setEmployeeId(rs.getInt("employee_id"));
        lr.setEmployeeName(rs.getString("full_name"));
        Date fd = rs.getDate("from_date"); if (fd != null) lr.setFromDate(fd.toLocalDate());
        Date td = rs.getDate("to_date"); if (td != null) lr.setToDate(td.toLocalDate());
        lr.setTotalDays(rs.getInt("total_days"));
        lr.setLeaveType(rs.getString("leave_type"));
        lr.setReason(rs.getString("reason"));
        lr.setStatus(rs.getString("status"));
        lr.setApprovedBy(rs.getString("approved_by"));
        Timestamp ts = rs.getTimestamp("created_at"); if (ts != null) lr.setCreatedAt(ts.toLocalDateTime());
        return lr;
    }
}
