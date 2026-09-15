package com.capstone.assessment.v3.answersheet.service;

import com.capstone.assessment.v3.answersheet.repository.V3AnswerSheetRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The staging profile cannot enable a new capture geometry against the ordinary school database. */
@Component
@Profile("v3 & v3-dynamic-staging")
public class V3DynamicStagingValidator implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final V3AnswerSheetRepository repository;
    public V3DynamicStagingValidator(JdbcTemplate jdbc,V3AnswerSheetRepository repository) {
        this.jdbc=jdbc;this.repository=repository;
    }
    @Override public void afterPropertiesSet() throws Exception {
        String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
        try(var connection=jdbc.getDataSource().getConnection()) {
            if(database==null || !database.matches("v3_dynamic_staging_[0-9]+")
                    || !connection.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:33319/"))
                throw new IllegalStateException("Dynamic staging requires its isolated loopback database on port 33319.");
        }
        var template=repository.findValidatedTemplate("A4").orElseThrow();
        if(!V3DynamicLayout.CODE.equals(template.code()) || !V3DynamicLayout.VERSION.equals(template.version())
                || template.qrPayloadVersion()!=3 || !V3DynamicLayout.SCANNER_VERSION.equals(template.minimumScannerVersion())
                || template.minimumItemCount()!=5 || template.maximumItemCount()!=V3DynamicLayout.MAX_QUESTIONS
                || template.regions().size()!=V3DynamicLayout.PAGE_CAPACITY+5)
            throw new IllegalStateException("Dynamic template seed does not match the implemented contract.");
        var slots=template.regions().stream().filter(r->"objective_bubbles".equals(r.type())).toList();
        if(slots.size()!=V3DynamicLayout.PAGE_CAPACITY)throw new IllegalStateException("Incomplete dynamic slots.");
        for(int i=0;i<slots.size();i++) {
            var region=slots.get(i);double y=V3DynamicLayout.BODY_TOP-V3DynamicLayout.RADIUS-i*V3DynamicLayout.ROW_PITCH;
            if(Math.abs(region.geometry().path("bubble_centers_pt").get(0).get(1).asDouble()-y)>.001
                    || !V3DynamicLayout.sha256(region.geometryJson()).equals(region.geometryHash()))
                throw new IllegalStateException("Dynamic region geometry/hash mismatch.");
        }
    }
}
