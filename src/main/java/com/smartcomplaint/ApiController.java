
package com.smartcomplaint;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174"
})
public class ApiController {

    private final JdbcTemplate jdbcTemplate;

    public ApiController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users",
            Integer.class
        );

        return Map.of(
            "message", "Smart Complaint Management System backend is running!",
            "database", "Connected successfully",
            "usersInDatabase", userCount
        );
    }
}
