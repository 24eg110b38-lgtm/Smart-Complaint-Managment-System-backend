
package com.smartcomplaint;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:5174"
})
public class ApiController {

    private final DataSource dataSource;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public ApiController(DataSource dataSource, JwtService jwtService) {
        this.dataSource = dataSource;
        this.jwtService = jwtService;
    }

    // REGISTER
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody Map<String, Object> body) {

        String fullName = stringValue(body.get("full_name"));
        if (fullName.isBlank()) {
            fullName = stringValue(body.get("fullName"));
        }

        String email = stringValue(body.get("email"))
                .trim().toLowerCase();
        String password = stringValue(body.get("password"));

        if (fullName.isBlank() || email.isBlank()
                || password.isBlank()) {
            return response(HttpStatus.BAD_REQUEST,
                    "Please provide your name, email and password.");
        }

        if (password.length() < 8) {
            return response(HttpStatus.BAD_REQUEST,
                    "Password must contain at least 8 characters.");
        }

        String sql = """
                INSERT INTO users (full_name, email, password, role)
                VALUES (?, ?, ?, 'USER')
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setString(3,
                    passwordEncoder.encode(password));

            statement.executeUpdate();

            return response(HttpStatus.CREATED,
                    "Registration successful!");

        } catch (SQLException e) {
            if (e.getSQLState() != null
                    && e.getSQLState().startsWith("23")) {
                return response(HttpStatus.CONFLICT,
                        "This email is already registered.");
            }

            e.printStackTrace();
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Registration failed.");
        }
    }

    // LOGIN WITH JWT TOKEN
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, Object> body) {

        String email = stringValue(body.get("email"))
                .trim().toLowerCase();
        String password = stringValue(body.get("password"));

        if (email.isBlank() || password.isBlank()) {
            return response(HttpStatus.BAD_REQUEST,
                    "Please provide your email and password.");
        }

        String sql = """
                SELECT id, full_name, email, password, role
                FROM users
                WHERE email = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return response(HttpStatus.UNAUTHORIZED,
                            "Invalid email or password.");
                }

                String storedPassword = rs.getString("password");
                boolean matches;

                if (storedPassword != null
                        && storedPassword.startsWith("$2")) {
                    matches = passwordEncoder.matches(
                            password, storedPassword);
                } else {
                    // Support accounts created before BCrypt was used.
                    matches = Objects.equals(password, storedPassword);

                    if (matches) {
                        try (PreparedStatement update =
                                     connection.prepareStatement(
                                             "UPDATE users SET password = ? WHERE id = ?")) {
                            update.setString(1,
                                    passwordEncoder.encode(password));
                            update.setInt(2, rs.getInt("id"));
                            update.executeUpdate();
                        }
                    }
                }

                if (!matches) {
                    return response(HttpStatus.UNAUTHORIZED,
                            "Invalid email or password.");
                }

                int userId = rs.getInt("id");
                String userEmail = rs.getString("email");
                String role = rs.getString("role");

                String token = jwtService.generateToken(
                        userId, userEmail, role);

                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", userId);
                user.put("full_name", rs.getString("full_name"));
                user.put("email", userEmail);
                user.put("role", role);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", "Login successful!");
                result.put("token", token);
                result.put("user", user);

                return ResponseEntity.ok(result);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Login failed.");
        }
    }

    // SUBMIT COMPLAINT
    @PostMapping("/complaints")
    public ResponseEntity<Map<String, Object>> submitComplaint(
            @RequestBody Map<String, Object> body) {

        int userId = intValue(body.get("userId"));
        if (userId == 0) {
            userId = intValue(body.get("user_id"));
        }

        String title = stringValue(body.get("title"));
        String description = stringValue(body.get("description"));
        String category = stringValue(body.get("category"));

        if (userId <= 0 || title.isBlank()
                || description.isBlank() || category.isBlank()) {
            return response(HttpStatus.BAD_REQUEST,
                    "Please provide all complaint details.");
        }

        String sql = """
                INSERT INTO complaints
                (user_id, title, description, category, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, userId);
            statement.setString(2, title);
            statement.setString(3, description);
            statement.setString(4, category);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                int complaintId = keys.next() ? keys.getInt(1) : 0;

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message",
                        "Complaint submitted successfully!");
                result.put("complaint_id", complaintId);

                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(result);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not submit complaint.");
        }
    }

    // TRACK COMPLAINTS
    @GetMapping("/complaints")
    public ResponseEntity<?> getComplaints(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer user_id) {

        if (userId == null) {
            userId = user_id;
        }

        String sql = """
                SELECT id, user_id, title, description, category,
                       status, admin_remark, created_at, updated_at
                FROM complaints
                """;

        if (userId != null) {
            sql += " WHERE user_id = ?";
        }

        sql += " ORDER BY created_at DESC";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            if (userId != null) {
                statement.setInt(1, userId);
            }

            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> complaints =
                        new ArrayList<>();

                while (rs.next()) {
                    complaints.add(complaintFromRow(rs));
                }

                return ResponseEntity.ok(complaints);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load complaints.");
        }
    }

    // ADMIN: VIEW COMPLAINTS
    @GetMapping("/admin/complaints")
    public ResponseEntity<?> getAdminComplaints(
            @RequestParam int adminId) {

        if (!isAdmin(adminId)) {
            return response(HttpStatus.FORBIDDEN,
                    "Administrator access required.");
        }

        String sql = """
                SELECT id, user_id, title, description, category,
                       status, admin_remark, created_at, updated_at
                FROM complaints
                ORDER BY created_at DESC
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            List<Map<String, Object>> complaints =
                    new ArrayList<>();

            while (rs.next()) {
                complaints.add(complaintFromRow(rs));
            }

            return ResponseEntity.ok(complaints);

        } catch (SQLException e) {
            e.printStackTrace();
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not load administrator complaints.");
        }
    }

    // ADMIN: UPDATE COMPLAINT
    @PutMapping("/admin/complaints/{id}")
    public ResponseEntity<Map<String, Object>> updateComplaint(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {

        int adminId = intValue(body.get("adminId"));
        String status = stringValue(body.get("status"))
                .toUpperCase();
        String remark = stringValue(body.get("admin_remark"));

        if (!isAdmin(adminId)) {
            return response(HttpStatus.FORBIDDEN,
                    "Administrator access required.");
        }

        if (!Set.of("PENDING", "IN_PROGRESS", "RESOLVED")
                .contains(status)) {
            return response(HttpStatus.BAD_REQUEST,
                    "Invalid complaint status.");
        }

        String sql = """
                UPDATE complaints
                SET status = ?, admin_remark = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setString(2, remark);
            statement.setInt(3, id);

            if (statement.executeUpdate() == 0) {
                return response(HttpStatus.NOT_FOUND,
                        "Complaint not found.");
            }

            return response(HttpStatus.OK,
                    "Complaint updated successfully!");

        } catch (SQLException e) {
            e.printStackTrace();
            return response(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not update complaint.");
        }
    }

    private boolean isAdmin(int userId) {
        String sql = "SELECT role FROM users WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, userId);

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next()
                        && "ADMIN".equalsIgnoreCase(
                                rs.getString("role"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Map<String, Object> complaintFromRow(ResultSet rs)
            throws SQLException {

        Map<String, Object> complaint = new LinkedHashMap<>();
        complaint.put("id", rs.getInt("id"));
        complaint.put("user_id", rs.getInt("user_id"));
        complaint.put("title", rs.getString("title"));
        complaint.put("description", rs.getString("description"));
        complaint.put("category", rs.getString("category"));
        complaint.put("status", rs.getString("status"));
        complaint.put("admin_remark", rs.getString("admin_remark"));
        complaint.put("created_at", rs.getTimestamp("created_at"));
        complaint.put("updated_at", rs.getTimestamp("updated_at"));

        return complaint;
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }

        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private ResponseEntity<Map<String, Object>> response(
            @NonNull HttpStatus status, String message) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);

        return ResponseEntity.status(status).body(result);
    }
}
