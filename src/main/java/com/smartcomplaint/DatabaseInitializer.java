package com.smartcomplaint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            LOGGER.info("Verifying database schema...");

            statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT NOT NULL AUTO_INCREMENT,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL DEFAULT 'USER',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE (email)
                )
            """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS complaints (
                    id INT NOT NULL AUTO_INCREMENT,
                    user_id INT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    description TEXT NOT NULL,
                    category VARCHAR(100) NOT NULL,
                    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                    admin_remark VARCHAR(500) DEFAULT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    CONSTRAINT fk_complaints_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
                )
            """);

            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users WHERE email = 'admin@gmail.com'")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    statement.execute("""
                        INSERT INTO users (full_name, email, password, role)
                        VALUES ('System Administrator', 'admin@gmail.com', '$2a$10$92z3Sgjgk4rr5cMKKxpUSeyTj5/Q1dmVi7QsVepaqw.HvOsTPAhQC', 'ADMIN')
                    """);
                    LOGGER.info("Default admin user created: admin@gmail.com / Admin@123");
                }
            }

            LOGGER.info("Database schema verification completed successfully.");

        } catch (Exception e) {
            LOGGER.warn("Database initialization notice: {}", e.getMessage());
        }
    }
}
