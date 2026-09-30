package com.capstone.assessment.v3.mobile.repository;

import com.capstone.assessment.v3.auth.model.V3AuthenticatedUser;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.AcceptedAnswer;
import com.capstone.assessment.v3.mobile.dto.V3EvaluationReference.Criterion;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("v3")
public class V3EvaluationReferenceRepository {
    private final JdbcTemplate jdbc;
    public V3EvaluationReferenceRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean active(V3AuthenticatedUser u) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id=u.role_id JOIN statuses s ON s.status_id=u.status_id
                WHERE u.user_id=? AND u.school_id=? AND r.role_name='teacher' AND s.status_name='active'
                """,Integer.class,u.userId(),u.schoolId())==1;
    }
    public Optional<Assignment> assignment(V3AuthenticatedUser u,String uuid) {
        return assignment(u,uuid,false);
    }
    public Optional<Assignment> assignment(V3AuthenticatedUser u,String uuid,boolean lock) {
        return jdbc.query("""
                SELECT t.test_id,t.version_number,t.status,d.assignment_status FROM test_assignments d
                JOIN class_assignments a ON a.class_assignment_id=d.class_assignment_id
                JOIN classes c ON c.class_id=a.class_id JOIN sections s ON s.section_id=c.section_id
                JOIN tests t ON t.test_id=d.test_id
                WHERE d.assignment_uuid=? AND a.user_id=? AND t.school_id=? AND s.school_id=?
                """+(lock?" FOR UPDATE":""),(rs,n)->new Assignment(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getString(4)),uuid,u.userId(),u.schoolId(),u.schoolId()).stream().findFirst();
    }
    public List<QuestionRow> questions(long test) {
        return questions(test,false);
    }
    public List<QuestionRow> questions(long test,boolean lock) {
        // Contract 3.1 sends the correct option key for the phone's preliminary score.
        // answer_keys.answer_explanation (the model solution) is still never selected.
        return jdbc.query("""
                SELECT q.question_uuid,q.maximum_points,q.rubric_id,q.expected_response_count,
                       type.question_type_code,k.answer_key_type,k.rubric_id AS key_rubric_id,
                       correct_option.option_key,q.question_id
                FROM questions q JOIN test_parts p ON p.test_part_id=q.test_part_id
                JOIN question_types type ON type.question_type_id=q.question_type_id
                LEFT JOIN answer_keys k ON k.question_id=q.question_id
                LEFT JOIN question_options correct_option ON correct_option.question_option_id=k.correct_question_option_id
                WHERE p.test_id=? ORDER BY q.question_uuid LIMIT 201
                """+(lock?" FOR UPDATE":""),(rs,n)->new QuestionRow(rs.getString(1),rs.getBigDecimal(2),rs.getObject(3)==null?null:rs.getLong(3),
                rs.getObject(4)==null?null:rs.getInt(4),rs.getString(5),rs.getString(6),rs.getObject(7)==null?null:rs.getLong(7),
                rs.getString(8),rs.getLong(9)),test);
    }
    /** Active accepted answers of every question in the test, by question_id, in answer order. */
    public java.util.Map<Long,List<AcceptedAnswer>> acceptedAnswers(long test,boolean lock) {
        var byQuestion=new java.util.LinkedHashMap<Long,List<AcceptedAnswer>>();
        jdbc.query("""
                SELECT a.question_id,a.accepted_text,a.matching_mode,a.is_case_sensitive,a.points
                FROM accepted_answers a JOIN questions q ON q.question_id=a.question_id
                JOIN test_parts p ON p.test_part_id=q.test_part_id
                WHERE p.test_id=? AND a.is_active=1
                ORDER BY a.question_id,a.answer_order IS NULL,a.answer_order,a.accepted_answer_id
                """+(lock?" FOR UPDATE":""),rs->{
            byQuestion.computeIfAbsent(rs.getLong(1),id->new java.util.ArrayList<>()).add(
                    new AcceptedAnswer(rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getBigDecimal(5)));
        },test);
        return byQuestion;
    }
    public Optional<RubricRow> rubric(long id,String school) {
        return rubric(id,school,false);
    }
    public Optional<RubricRow> rubric(long id,String school,boolean lock) {
        return jdbc.query("SELECT rubric_id,rubric_name,total_points,rubric_status FROM rubrics WHERE rubric_id=? AND school_id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new RubricRow(rs.getLong(1),rs.getString(2),rs.getBigDecimal(3),rs.getString(4)),id,school).stream().findFirst();
    }
    public List<Criterion> criteria(long rubric) {
        return criteria(rubric,false);
    }
    public List<Criterion> criteria(long rubric,boolean lock) {
        return jdbc.query("SELECT rubric_criterion_id,criterion_name,maximum_points,is_required,criterion_description,level_definition FROM rubric_criteria WHERE rubric_id=? ORDER BY criterion_order,rubric_criterion_id LIMIT 101"+(lock?" FOR UPDATE":""),
                (rs,n)->new Criterion(rs.getLong(1),rs.getString(2),rs.getBigDecimal(3),rs.getBoolean(4),rs.getString(5),levels(rs.getString(6))),rubric);
    }
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
    /** level_definition is a JSON column (json_valid CHECK); passed through as a tree. */
    private static com.fasterxml.jackson.databind.JsonNode levels(String json) {
        if(json==null || json.isBlank()) return null;
        try{return JSON.readTree(json);}catch(com.fasterxml.jackson.core.JsonProcessingException e){return null;}
    }
    public record Assignment(long testId,int version,String testStatus,String assignmentStatus) { }
    public record QuestionRow(String uuid,BigDecimal maximum,Long rubricId,Integer expectedCount,String type,String keyType,Long keyRubricId,
            String correctOptionKey,long questionId) { }
    public record RubricRow(long id,String name,BigDecimal total,String status) { }
}
