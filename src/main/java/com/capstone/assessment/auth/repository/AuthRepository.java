package com.capstone.assessment.auth.repository;

import com.capstone.assessment.auth.dto.CurrentUserResponse;
import com.capstone.assessment.auth.dto.TeacherAccountResponse;
import com.capstone.assessment.auth.dto.TeacherRegistrationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CurrentUserResponse> findUserByEmail(String email) {
        String sql = """
                SELECT user_id,
                       first_name,
                       last_name,
                       email,
                       role,
                       status
                FROM `user`
                WHERE email = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.of(mapCurrentUserResponse(rs)) : Optional.empty(),
                email
        );
    }

    public Optional<CurrentUserResponse> findUserById(Long userId) {
        String sql = """
                SELECT user_id,
                       first_name,
                       last_name,
                       email,
                       role,
                       status
                FROM `user`
                WHERE user_id = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.of(mapCurrentUserResponse(rs)) : Optional.empty(),
                userId
        );
    }

    public Optional<String> findPasswordByEmail(String email) {
        String sql = """
                SELECT `password`
                FROM `user`
                WHERE email = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.ofNullable(rs.getString("password")) : Optional.empty(),
                email
        );
    }

    public boolean emailExists(String email) {
        String sql = """
                SELECT COUNT(*)
                FROM `user`
                WHERE email = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    public Long createPendingTeacher(TeacherRegistrationRequest request) {
        String sql = """
                INSERT INTO `user` (first_name, last_name, gender, date_birth, email, `password`, role, status)
                VALUES (?, ?, ?, ?, ?, ?, 'teacher', 'pending')
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatement.setString(1, request.firstName());
            preparedStatement.setString(2, request.lastName());
            preparedStatement.setString(3, request.gender());
            preparedStatement.setString(4, request.dateBirth());
            preparedStatement.setString(5, request.email());
            preparedStatement.setString(6, request.password());
            return preparedStatement;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(), "Failed to retrieve generated user_id").longValue();
    }

    public List<TeacherAccountResponse> findTeachers() {
        String sql = """
                SELECT user_id,
                       first_name,
                       last_name,
                       gender,
                       date_birth,
                       email,
                       role,
                       status
                FROM `user`
                WHERE role = 'teacher'
                ORDER BY last_name, first_name
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapTeacherAccountResponse(rs));
    }

    public Optional<TeacherAccountResponse> findTeacherById(Long userId) {
        String sql = """
                SELECT user_id,
                       first_name,
                       last_name,
                       gender,
                       date_birth,
                       email,
                       role,
                       status
                FROM `user`
                WHERE user_id = ?
                  AND role = 'teacher'
                """;

        return jdbcTemplate.query(
                sql,
                rs -> rs.next() ? Optional.of(mapTeacherAccountResponse(rs)) : Optional.empty(),
                userId
        );
    }

    public void updateTeacherStatus(Long userId, String status) {
        String sql = """
                UPDATE `user`
                SET status = ?
                WHERE user_id = ?
                  AND role = 'teacher'
                """;

        jdbcTemplate.update(sql, status, userId);
    }

    private CurrentUserResponse mapCurrentUserResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CurrentUserResponse(
                rs.getLong("user_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status")
        );
    }

    private TeacherAccountResponse mapTeacherAccountResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TeacherAccountResponse(
                rs.getLong("user_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("gender"),
                rs.getString("date_birth"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status")
        );
    }
}
