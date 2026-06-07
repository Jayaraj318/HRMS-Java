package com.hrms.model;

public class User {
    private int id;
    private String username;
    private String role;
    private Integer employeeId;

    public User(int id, String username, String role, Integer employeeId) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.employeeId = employeeId;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public Integer getEmployeeId() { return employeeId; }
}
