package com.hrms.server;

import com.hrms.dao.UserDAO;
import com.hrms.dao.EmployeeDAO;
import com.hrms.model.User;
import com.hrms.model.Employee;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.hrms.dao.DepartmentDAO;
import com.hrms.model.Department;
import com.hrms.dao.AttendanceDAO;
import com.hrms.model.Attendance;
import com.hrms.dao.LeaveDAO;
import com.hrms.model.LeaveRequest;

public class AppServer {
    private static final int PORT = 8080;
    private static Map<String, User> sessions = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/static", new StaticHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/employees", new EmployeeApiHandler());
        server.createContext("/api/departments", new DepartmentApiHandler());
        server.createContext("/api/attendance", new AttendanceApiHandler());
        server.createContext("/api/leaves", new LeaveApiHandler());
        server.createContext("/api/me", new MeHandler());
        server.createContext("/api/dashboard/stats", new DashboardApiHandler());
        server.setExecutor(null);
        System.out.println("Server started at http://localhost:" + PORT);
        server.start();
    }

    static class RootHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            Headers h = ex.getResponseHeaders();
            h.add("Location", "/static/index.html");
            ex.sendResponseHeaders(302, -1);
        }
    }

    static class StaticHandler implements HttpHandler {
        private static final String ROOT = "web/static";

        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath().replaceFirst("/static", "");
            if (path.equals("") || path.equals("/"))
                path = "/index.html";
            File f = new File(ROOT + path);
            if (!f.exists() || f.isDirectory()) {
                byte[] notFound = "404 Not Found".getBytes();
                ex.sendResponseHeaders(404, notFound.length);
                ex.getResponseBody().write(notFound);
                ex.close();
                return;
            }
            String mime = URLConnection.guessContentTypeFromName(f.getName());
            if (mime == null)
                mime = "application/octet-stream";
            ex.getResponseHeaders().add("Content-Type", mime + ";charset=UTF-8");
            byte[] bytes = Files.readAllBytes(f.toPath());
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            Map<String, String> form = parseForm(ex);
            String username = form.getOrDefault("username", "");
            String password = form.getOrDefault("password", "");
            User user = UserDAO.authenticate(username, password);
            if (user != null) {
                String sid = UUID.randomUUID().toString();
                sessions.put(sid, user);
                Headers headers = ex.getResponseHeaders();
                headers.add("Set-Cookie", "SESSIONID=" + sid + "; Path=/; HttpOnly");
                byte[] ok = "{\"status\":\"ok\"}".getBytes();
                headers.add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(200, ok.length);
                ex.getResponseBody().write(ok);
                ex.close();
            } else {
                byte[] bad = "{\"status\":\"error\",\"message\":\"Invalid Username or Password\"}".getBytes();
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(401, bad.length);
                ex.getResponseBody().write(bad);
                ex.close();
            }
        }
    }

    static class LogoutHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String sid = getSessionId(ex);
            if (sid != null)
                sessions.remove(sid);
            Headers headers = ex.getResponseHeaders();
            headers.add("Set-Cookie", "SESSIONID=deleted; Path=/; Max-Age=0");
            ex.sendResponseHeaders(200, -1);
        }
    }

    static class EmployeeApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String sid = getSessionId(ex);
            User u = sid == null ? null : sessions.get(sid);
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                // list / search / get by id
                String q = parseQuery(ex.getRequestURI().getQuery());
                String idParam = parseQueryForKey(ex.getRequestURI().getQuery(), "id");
                DateTimeFormatter df = DateTimeFormatter.ISO_DATE;
                if (idParam != null) {
                    try {
                        int id = Integer.parseInt(idParam);
                        // allow if admin or requesting own employee record
                        if (u == null || !("ADMIN".equalsIgnoreCase(u.getRole())
                                || (u.getEmployeeId() != null && u.getEmployeeId() == id))) {
                            ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                            ex.sendResponseHeaders(403, 0);
                            ex.close();
                            return;
                        }
                        Employee e = EmployeeDAO.findById(id);
                        if (e == null) {
                            ex.sendResponseHeaders(404, -1);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("{");
                        sb.append("\"id\":").append(e.getId()).append(",");
                        sb.append("\"empId\":\"").append(escape(e.getEmpId())).append("\",");
                        sb.append("\"fullName\":\"").append(escape(e.getFullName())).append("\",");
                        sb.append("\"email\":\"").append(escape(e.getEmail())).append("\",");
                        sb.append("\"phone\":\"").append(escape(e.getPhone())).append("\",");
                        sb.append("\"designation\":\"").append(escape(e.getDesignation())).append("\",");
                        sb.append("\"department\":\"").append(escape(e.getDepartment())).append("\",");
                        sb.append("\"joiningDate\":\"")
                                .append(e.getJoiningDate() == null ? "" : e.getJoiningDate().format(df)).append("\",");
                        sb.append("\"salary\":").append(e.getSalary()).append(",");
                        sb.append("\"photo\":\"").append(escape(e.getPhoto())).append("\"");
                        sb.append("}");
                        byte[] out = sb.toString().getBytes("UTF-8");
                        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                        ex.sendResponseHeaders(200, out.length);
                        ex.getResponseBody().write(out);
                        ex.close();
                        return;
                    } catch (NumberFormatException nfe) {
                        ex.sendResponseHeaders(400, -1);
                        return;
                    }
                }
                // list/search: only admin
                if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) {
                    sendError(ex, 403, "Unauthorized");
                    return;
                }
                List<Employee> list = EmployeeDAO.list(q);
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < list.size(); i++) {
                    Employee e = list.get(i);
                    sb.append("{");
                    sb.append("\"id\":").append(e.getId()).append(",");
                    sb.append("\"empId\":\"").append(escape(e.getEmpId())).append("\",");
                    sb.append("\"fullName\":\"").append(escape(e.getFullName())).append("\",");
                    sb.append("\"email\":\"").append(escape(e.getEmail())).append("\",");
                    sb.append("\"phone\":\"").append(escape(e.getPhone())).append("\",");
                    sb.append("\"designation\":\"").append(escape(e.getDesignation())).append("\",");
                    sb.append("\"department\":\"").append(escape(e.getDepartment())).append("\",");
                    sb.append("\"joiningDate\":\"")
                            .append(e.getJoiningDate() == null ? "" : e.getJoiningDate().format(df)).append("\",");
                    sb.append("\"salary\":").append(e.getSalary()).append(",");
                    sb.append("\"photo\":\"").append(escape(e.getPhoto())).append("\"");
                    sb.append("}");
                    if (i < list.size() - 1)
                        sb.append(",");
                }
                sb.append("]");
                byte[] out = sb.toString().getBytes("UTF-8");
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
                return;
            }

            // For write operations require admin
            if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) {
                byte[] forbidden = "{\"status\":\"error\",\"message\":\"Unauthorized\"}".getBytes();
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(403, forbidden.length);
                ex.getResponseBody().write(forbidden);
                ex.close();
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> form = parseForm(ex);
                String action = form.getOrDefault("action", "add");
                // Basic server-side validation
                String empId = form.getOrDefault("empId", "").trim();
                String fullName = form.getOrDefault("fullName", "").trim();
                String email = form.getOrDefault("email", "").trim();
                if ("add".equalsIgnoreCase(action)) {
                    if (empId.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
                        sendError(ex, 400, "empId, fullName and email are required");
                        return;
                    }
                    Employee e = mapToEmployee(form);
                    try {
                        boolean ok = EmployeeDAO.add(e);
                        writeJsonResponse(ex, ok);
                    } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                        sendError(ex, 409, "Duplicate employee ID or email");
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                } else if ("update".equalsIgnoreCase(action)) {
                    String idStr = form.get("id");
                    if (idStr == null || idStr.isEmpty()) {
                        sendError(ex, 400, "id required");
                        return;
                    }
                    if (empId.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
                        sendError(ex, 400, "empId, fullName and email are required");
                        return;
                    }
                    Employee e = mapToEmployee(form);
                    e.setId(Integer.parseInt(idStr));
                    try {
                        boolean ok = EmployeeDAO.update(e);
                        writeJsonResponse(ex, ok);
                    } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                        sendError(ex, 409, "Duplicate employee ID or email");
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                } else if ("delete".equalsIgnoreCase(action)) {
                    String idStr = form.get("id");
                    if (idStr == null) {
                        sendError(ex, 400, "id required");
                        return;
                    }
                    try {
                        boolean ok = EmployeeDAO.delete(Integer.parseInt(idStr));
                        writeJsonResponse(ex, ok);
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                }
            }

            ex.sendResponseHeaders(405, -1);
        }
    }

    static class DepartmentApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String sid = getSessionId(ex);
            User u = sid == null ? null : sessions.get(sid);
            String method = ex.getRequestMethod();
            // Only ADMIN can access departments APIs (both GET and POST)
            if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) {
                sendError(ex, 403, "Unauthorized");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                String idParam = parseQueryForKey(ex.getRequestURI().getQuery(), "id");
                if (idParam != null) {
                    try {
                        int id = Integer.parseInt(idParam);
                        Department d = DepartmentDAO.findById(id);
                        if (d == null) {
                            ex.sendResponseHeaders(404, -1);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append('{');
                        sb.append("\"id\":").append(d.getId()).append(',');
                        sb.append("\"departmentName\":\"").append(escape(d.getDepartmentName())).append('"')
                                .append(',');
                        sb.append("\"managerName\":\"").append(escape(d.getManagerName())).append('"').append(',');
                        sb.append("\"createdAt\":\"")
                                .append(d.getCreatedAt() == null ? "" : d.getCreatedAt().toString()).append('"');
                        sb.append('}');
                        byte[] out = sb.toString().getBytes("UTF-8");
                        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                        ex.sendResponseHeaders(200, out.length);
                        ex.getResponseBody().write(out);
                        ex.close();
                        return;
                    } catch (NumberFormatException nfe) {
                        ex.sendResponseHeaders(400, -1);
                        return;
                    }
                }
                List<Department> list = DepartmentDAO.list();
                StringBuilder sb = new StringBuilder();
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    Department d = list.get(i);
                    sb.append('{');
                    sb.append("\"id\":").append(d.getId()).append(',');
                    sb.append("\"departmentName\":\"").append(escape(d.getDepartmentName())).append('"').append(',');
                    sb.append("\"managerName\":\"").append(escape(d.getManagerName())).append('"').append(',');
                    sb.append("\"createdAt\":\"").append(d.getCreatedAt() == null ? "" : d.getCreatedAt().toString())
                            .append('"');
                    sb.append('}');
                    if (i < list.size() - 1)
                        sb.append(',');
                }
                sb.append(']');
                byte[] out = sb.toString().getBytes("UTF-8");
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
                return;
            }

            if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) {
                sendError(ex, 403, "Unauthorized");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> form = parseForm(ex);
                String action = form.getOrDefault("action", "add");
                if ("add".equalsIgnoreCase(action)) {
                    String name = form.getOrDefault("departmentName", "").trim();
                    if (name.isEmpty()) {
                        sendError(ex, 400, "departmentName required");
                        return;
                    }
                    Department d = new Department();
                    d.setDepartmentName(name);
                    d.setManagerName(form.getOrDefault("managerName", ""));
                    try {
                        boolean ok = DepartmentDAO.add(d);
                        writeJsonResponse(ex, ok);
                    } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                        sendError(ex, 409, "Duplicate department");
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                } else if ("update".equalsIgnoreCase(action)) {
                    String idStr = form.get("id");
                    if (idStr == null) {
                        sendError(ex, 400, "id required");
                        return;
                    }
                    String name = form.getOrDefault("departmentName", "").trim();
                    if (name.isEmpty()) {
                        sendError(ex, 400, "departmentName required");
                        return;
                    }
                    Department d = new Department();
                    d.setId(Integer.parseInt(idStr));
                    d.setDepartmentName(name);
                    d.setManagerName(form.getOrDefault("managerName", ""));
                    try {
                        boolean ok = DepartmentDAO.update(d);
                        writeJsonResponse(ex, ok);
                    } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                        sendError(ex, 409, "Duplicate department");
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                } else if ("delete".equalsIgnoreCase(action)) {
                    String idStr = form.get("id");
                    if (idStr == null) {
                        sendError(ex, 400, "id required");
                        return;
                    }
                    try {
                        boolean ok = DepartmentDAO.delete(Integer.parseInt(idStr));
                        writeJsonResponse(ex, ok);
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                }
            }
            ex.sendResponseHeaders(405, -1);
        }
    }

    static class AttendanceApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String sid = getSessionId(ex);
            User u = sid == null ? null : sessions.get(sid);
            String method = ex.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String dateParam = parseQueryForKey(ex.getRequestURI().getQuery(), "date");
                java.time.LocalDate date = dateParam == null ? java.time.LocalDate.now()
                        : java.time.LocalDate.parse(dateParam);
                List<Attendance> list = new ArrayList<>();
                if (u != null && "ADMIN".equalsIgnoreCase(u.getRole())) {
                    list = AttendanceDAO.listByDate(date);
                } else if (u != null) {
                    Integer empId = null;
                    // map session user to employee id
                    try {
                        empId = com.hrms.dao.LeaveDAO.getEmployeeIdForUser(u.getId());
                    } catch (Exception e) {
                        empId = null;
                    }
                    if (empId != null)
                        list = AttendanceDAO.listByEmployeeAndDate(empId, date);
                }
                StringBuilder sb = new StringBuilder();
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    Attendance a = list.get(i);
                    sb.append('{');
                    sb.append("\"id\":").append(a.getId()).append(',');
                    sb.append("\"employeeId\":").append(a.getEmployeeId()).append(',');
                    sb.append("\"employeeName\":\"").append(escape(a.getEmployeeName())).append('\"').append(',');
                    sb.append("\"attendanceDate\":\"")
                            .append(a.getAttendanceDate() == null ? "" : a.getAttendanceDate().toString()).append('\"')
                            .append(',');
                    sb.append("\"checkIn\":\"").append(a.getCheckIn() == null ? "" : a.getCheckIn().toString())
                            .append('\"').append(',');
                    sb.append("\"checkOut\":\"").append(a.getCheckOut() == null ? "" : a.getCheckOut().toString())
                            .append('\"').append(',');
                    sb.append("\"status\":\"").append(a.getStatus() == null ? "" : a.getStatus()).append('\"');
                    sb.append('}');
                    if (i < list.size() - 1)
                        sb.append(',');
                }
                sb.append(']');
                byte[] out = sb.toString().getBytes("UTF-8");
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
                return;
            }

            if (u == null) {
                sendError(ex, 403, "Unauthorized");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> form = parseForm(ex);
                String action = form.getOrDefault("action", "checkin");
                String empIdStr = form.get("employeeId");
                if (empIdStr == null) {
                    sendError(ex, 400, "employeeId required");
                    return;
                }
                int empId = Integer.parseInt(empIdStr);
                if (!"ADMIN".equalsIgnoreCase(u.getRole())) {
                    Integer myEmpId = com.hrms.dao.LeaveDAO.getEmployeeIdForUser(u.getId());
                    if (myEmpId == null || myEmpId != empId) {
                        sendError(ex, 403, "Unauthorized");
                        return;
                    }
                }
                try {
                    if ("checkin".equalsIgnoreCase(action)) {
                        boolean ok = AttendanceDAO.checkIn(empId, java.time.LocalDateTime.now());
                        writeJsonResponse(ex, ok);
                        return;
                    } else if ("checkout".equalsIgnoreCase(action)) {
                        boolean ok = AttendanceDAO.checkOut(empId, java.time.LocalDateTime.now());
                        writeJsonResponse(ex, ok);
                        return;
                    }
                } catch (Exception sqe) {
                    sqe.printStackTrace();
                    sendError(ex, 500, "Database error");
                    return;
                }
            }

            ex.sendResponseHeaders(405, -1);
        }
    }

    static class LeaveApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String sid = getSessionId(ex);
            User u = sid == null ? null : sessions.get(sid);
            String method = ex.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String idParam = parseQueryForKey(ex.getRequestURI().getQuery(), "id");
                String q = parseQuery(ex.getRequestURI().getQuery());
                String status = parseQueryForKey(ex.getRequestURI().getQuery(), "status");
                if (idParam != null) {
                    try {
                        int id = Integer.parseInt(idParam);
                        LeaveRequest lr = LeaveDAO.findById(id);
                        if (lr == null) {
                            ex.sendResponseHeaders(404, -1);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append('{');
                        sb.append("\"id\":").append(lr.getId()).append(',');
                        sb.append("\"employeeId\":").append(lr.getEmployeeId()).append(',');
                        sb.append("\"employeeName\":\"").append(escape(lr.getEmployeeName())).append('\"').append(',');
                        sb.append("\"leaveType\":\"").append(escape(lr.getLeaveType())).append('\"').append(',');
                        sb.append("\"fromDate\":\"").append(lr.getFromDate() == null ? "" : lr.getFromDate().toString())
                                .append('\"').append(',');
                        sb.append("\"toDate\":\"").append(lr.getToDate() == null ? "" : lr.getToDate().toString())
                                .append('\"').append(',');
                        sb.append("\"totalDays\":").append(lr.getTotalDays()).append(',');
                        sb.append("\"reason\":\"").append(escape(lr.getReason())).append('\"').append(',');
                        sb.append("\"status\":\"").append(lr.getStatus()).append('\"').append(',');
                        sb.append("\"approvedBy\":\"").append(escape(lr.getApprovedBy())).append('\"').append(',');
                        sb.append("\"createdAt\":\"")
                                .append(lr.getCreatedAt() == null ? "" : lr.getCreatedAt().toString()).append('\"');
                        sb.append('}');
                        byte[] out = sb.toString().getBytes("UTF-8");
                        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                        ex.sendResponseHeaders(200, out.length);
                        ex.getResponseBody().write(out);
                        ex.close();
                        return;
                    } catch (NumberFormatException nfe) {
                        ex.sendResponseHeaders(400, -1);
                        return;
                    }
                }
                List<LeaveRequest> list = new ArrayList<>();
                if (u != null && "ADMIN".equalsIgnoreCase(u.getRole())) {
                    list = LeaveDAO.listAll(q, status);
                } else if (u != null) {
                    Integer empId = LeaveDAO.getEmployeeIdForUser(u.getId());
                    if (empId != null)
                        list = LeaveDAO.listByEmployee(empId, status);
                }
                StringBuilder sb = new StringBuilder();
                sb.append('[');
                for (int i = 0; i < list.size(); i++) {
                    LeaveRequest lr = list.get(i);
                    sb.append('{');
                    sb.append("\"id\":").append(lr.getId()).append(',');
                    sb.append("\"employeeId\":").append(lr.getEmployeeId()).append(',');
                    sb.append("\"employeeName\":\"").append(escape(lr.getEmployeeName())).append('\"').append(',');
                    sb.append("\"leaveType\":\"").append(escape(lr.getLeaveType())).append('\"').append(',');
                    sb.append("\"fromDate\":\"").append(lr.getFromDate() == null ? "" : lr.getFromDate().toString())
                            .append('\"').append(',');
                    sb.append("\"toDate\":\"").append(lr.getToDate() == null ? "" : lr.getToDate().toString())
                            .append('\"').append(',');
                    sb.append("\"totalDays\":").append(lr.getTotalDays()).append(',');
                    sb.append("\"reason\":\"").append(escape(lr.getReason())).append('\"').append(',');
                    sb.append("\"status\":\"").append(lr.getStatus()).append('\"').append(',');
                    sb.append("\"approvedBy\":\"").append(escape(lr.getApprovedBy())).append('\"').append(',');
                    sb.append("\"createdAt\":\"").append(lr.getCreatedAt() == null ? "" : lr.getCreatedAt().toString())
                            .append('\"');
                    sb.append('}');
                    if (i < list.size() - 1)
                        sb.append(',');
                }
                sb.append(']');
                byte[] out = sb.toString().getBytes("UTF-8");
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                ex.sendResponseHeaders(200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
                return;
            }

            if (u == null) {
                sendError(ex, 403, "Unauthorized");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                Map<String, String> form = parseForm(ex);
                String action = form.getOrDefault("action", "apply");
                if ("apply".equalsIgnoreCase(action)) {
                    if ("ADMIN".equalsIgnoreCase(u.getRole())) {
                        sendError(ex, 403, "Admins cannot apply leave");
                        return;
                    }

                    String leaveType = form.getOrDefault("leaveType", "").trim();
                    String from = form.getOrDefault("fromDate", "").trim();
                    String to = form.getOrDefault("toDate", "").trim();
                    String reason = form.getOrDefault("reason", "").trim();
                    if (leaveType.isEmpty() || from.isEmpty() || to.isEmpty() || reason.isEmpty()) {
                        sendError(ex, 400, "leaveType, fromDate, toDate and reason are required");
                        return;
                    }
                    try {
                        java.time.LocalDate fd = java.time.LocalDate.parse(from);
                        java.time.LocalDate td = java.time.LocalDate.parse(to);
                        if (td.isBefore(fd)) {
                            sendError(ex, 400, "toDate cannot be before fromDate");
                            return;
                        }
                        long days = java.time.temporal.ChronoUnit.DAYS.between(fd, td) + 1;
                        Integer empId = null;
                        String empIdStr = form.get("employeeId");
                        if (empIdStr != null && !empIdStr.isEmpty())
                            empId = Integer.parseInt(empIdStr);
                        if (empId == null) {
                            empId = u.getEmployeeId();
                            if (empId == null)
                                empId = LeaveDAO.getEmployeeIdForUser(u.getId());
                        }
                        LeaveRequest lr = new LeaveRequest();
                        lr.setEmployeeId(empId);
                        lr.setLeaveType(leaveType);
                        lr.setFromDate(fd);
                        lr.setToDate(td);
                        lr.setTotalDays((int) days);
                        lr.setReason(reason);
                        lr.setStatus("PENDING");
                        boolean ok = LeaveDAO.apply(lr);
                        writeJsonResponse(ex, ok);
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                } else if ("approve".equalsIgnoreCase(action) || "reject".equalsIgnoreCase(action)) {
                    if (u == null || !"ADMIN".equalsIgnoreCase(u.getRole())) {
                        sendError(ex, 403, "Unauthorized");
                        return;
                    }
                    String idStr = form.get("id");
                    if (idStr == null || idStr.isEmpty()) {
                        sendError(ex, 400, "id required");
                        return;
                    }
                    try {
                        int id = Integer.parseInt(idStr);
                        boolean ok = false;
                        if ("approve".equalsIgnoreCase(action))
                            ok = LeaveDAO.approve(id, u.getUsername());
                        else
                            ok = LeaveDAO.reject(id, u.getUsername());
                        writeJsonResponse(ex, ok);
                    } catch (Exception sqe) {
                        sqe.printStackTrace();
                        sendError(ex, 500, "Database error");
                    }
                    return;
                }
            }

            ex.sendResponseHeaders(405, -1);
        }
    }

    static class MeHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            String sid = getSessionId(ex);
            User u = sid == null ? null : sessions.get(sid);
            if (u == null) {
                ex.sendResponseHeaders(401, -1);
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append("\"id\":").append(u.getId()).append(',');
            sb.append("\"username\":\"").append(escape(u.getUsername())).append('\"').append(',');
            sb.append("\"role\":\"").append(escape(u.getRole())).append('\"').append(',');
            sb.append("\"employeeId\":").append(u.getEmployeeId() == null ? "null" : u.getEmployeeId());
            sb.append('}');
            byte[] out = sb.toString().getBytes("UTF-8");
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        }
    }

    static class DashboardApiHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            java.time.LocalDate date = java.time.LocalDate.now();
            int totalEmployees = com.hrms.dao.EmployeeDAO.count();
            int totalDepartments = com.hrms.dao.DepartmentDAO.count();
            int presentToday = com.hrms.dao.AttendanceDAO.countPresent(date);
            int absentToday = com.hrms.dao.AttendanceDAO.countAbsent(date);
            int pendingLeaves = com.hrms.dao.LeaveDAO.countByStatus("PENDING");
            int approvedLeaves = com.hrms.dao.LeaveDAO.countByStatus("APPROVED");
            int rejectedLeaves = com.hrms.dao.LeaveDAO.countByStatus("REJECTED");
            // Fallback: if no explicit absent records, derive from total - present
            if (absentToday == 0 && totalEmployees >= 0) {
                int derived = totalEmployees - presentToday;
                if (derived < 0)
                    derived = 0;
                absentToday = derived;
            }
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            sb.append("\"totalEmployees\":").append(totalEmployees).append(',');
            sb.append("\"totalDepartments\":").append(totalDepartments).append(',');
            sb.append("\"presentToday\":").append(presentToday).append(',');
            sb.append("\"absentToday\":").append(absentToday);
            sb.append(',');
            sb.append("\"pendingLeaves\":").append(pendingLeaves).append(',');
            sb.append("\"approvedLeaves\":").append(approvedLeaves).append(',');
            sb.append("\"rejectedLeaves\":").append(rejectedLeaves);
            sb.append('}');
            byte[] out = sb.toString().getBytes("UTF-8");
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        }
    }

    // Helpers
    private static void writeJsonResponse(HttpExchange ex, boolean ok) throws IOException {
        String body = ok ? "{\"status\":\"ok\"}" : "{\"status\":\"error\"}";
        byte[] out = body.getBytes("UTF-8");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private static String getSessionId(HttpExchange ex) {
        Headers h = ex.getRequestHeaders();
        List<String> cookies = h.get("Cookie");
        if (cookies == null)
            return null;
        for (String c : cookies) {
            String[] parts = c.split(";");
            for (String p : parts) {
                p = p.trim();
                if (p.startsWith("SESSIONID="))
                    return p.substring("SESSIONID=".length());
            }
        }
        return null;
    }

    private static Map<String, String> parseForm(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        InputStream in = ex.getRequestBody();
        String body = new BufferedReader(new InputStreamReader(in, "UTF-8")).lines().reduce("", (a, b) -> a + b);
        String[] pairs = body.split("&");
        for (String p : pairs) {
            if (p.isEmpty())
                continue;
            String[] kv = p.split("=", 2);
            String k = URLDecoder.decode(kv[0], "UTF-8");
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
            map.put(k, v);
        }
        return map;
    }

    private static String parseQuery(String q) {
        if (q == null)
            return null;
        String[] parts = q.split("&");
        for (String p : parts) {
            if (p.startsWith("search=")) {
                try {
                    return URLDecoder.decode(p.substring(7), "UTF-8");
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void sendError(HttpExchange ex, int code, String message) throws IOException {
        String body = "{\"status\":\"error\",\"message\":\"" + message.replace("\"", "\\\"") + "\"}";
        byte[] out = body.getBytes("UTF-8");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private static String parseQueryForKey(String q, String key) {
        if (q == null)
            return null;
        String[] parts = q.split("&");
        for (String p : parts) {
            if (p.startsWith(key + "=")) {
                try {
                    return URLDecoder.decode(p.substring(key.length() + 1), "UTF-8");
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Employee mapToEmployee(Map<String, String> form) {
        Employee e = new Employee();
        e.setEmpId(form.getOrDefault("empId", ""));
        e.setFullName(form.getOrDefault("fullName", ""));
        e.setEmail(form.getOrDefault("email", ""));
        e.setPhone(form.getOrDefault("phone", ""));
        e.setDesignation(form.getOrDefault("designation", ""));
        e.setDepartment(form.getOrDefault("department", ""));
        String jd = form.get("joiningDate");
        if (jd != null && !jd.isEmpty()) {
            e.setJoiningDate(java.time.LocalDate.parse(jd));
        }
        try {
            e.setSalary(Double.parseDouble(form.getOrDefault("salary", "0")));
        } catch (Exception ex) {
            e.setSalary(0);
        }
        e.setPhoto(form.getOrDefault("photo", ""));
        return e;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
