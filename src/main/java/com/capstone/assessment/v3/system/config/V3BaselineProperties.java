package com.capstone.assessment.v3.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.v3.baseline")
public class V3BaselineProperties {

    private String expectedDatabase = "performance_assessment_v3_db";
    private int expectedTableCount = 67;
    private int expectedForeignKeyCount = 163;
    private int expectedCheckConstraintCount = 87;
    private int expectedUniqueConstraintCount = 99;
    private int expectedDynamicTableCount = 6;
    private int expectedHardeningColumnCount = 16;
    private int expectedHardeningConstraintCount = 15;
    private int expectedSchoolScopedSectionRuleCount = 3;
    private int expectedAcademicCalendarColumnCount = 20;
    private int expectedAcademicCalendarConstraintCount = 16;
    private int expectedPaperSizeCount = 3;
    private int expectedPaperSizeSeedCount = 3;
    private int expectedValidatedTemplateRegionCount = 15;
    private int expectedQuestionTypeCount = 5;
    private int expectedOmrTemplateCount = 1;
    private int expectedApprovedOmrTemplateCount = 1;
    private int expectedUnapprovedOmrTemplateCount = 0;
    private int expectedPerformanceRuleSetCount = 4;
    private boolean validateOnStartup = true;

    public String getExpectedDatabase() {
        return expectedDatabase;
    }

    public void setExpectedDatabase(String expectedDatabase) {
        this.expectedDatabase = expectedDatabase;
    }

    public int getExpectedTableCount() {
        return expectedTableCount;
    }

    public void setExpectedTableCount(int expectedTableCount) {
        this.expectedTableCount = expectedTableCount;
    }

    public int getExpectedForeignKeyCount() {
        return expectedForeignKeyCount;
    }

    public void setExpectedForeignKeyCount(int expectedForeignKeyCount) {
        this.expectedForeignKeyCount = expectedForeignKeyCount;
    }

    public int getExpectedCheckConstraintCount() {
        return expectedCheckConstraintCount;
    }

    public void setExpectedCheckConstraintCount(int expectedCheckConstraintCount) {
        this.expectedCheckConstraintCount = expectedCheckConstraintCount;
    }

    public int getExpectedUniqueConstraintCount() {
        return expectedUniqueConstraintCount;
    }

    public void setExpectedUniqueConstraintCount(int expectedUniqueConstraintCount) {
        this.expectedUniqueConstraintCount = expectedUniqueConstraintCount;
    }

    public int getExpectedDynamicTableCount() {
        return expectedDynamicTableCount;
    }

    public void setExpectedDynamicTableCount(int expectedDynamicTableCount) {
        this.expectedDynamicTableCount = expectedDynamicTableCount;
    }

    public int getExpectedHardeningColumnCount() {
        return expectedHardeningColumnCount;
    }

    public void setExpectedHardeningColumnCount(int expectedHardeningColumnCount) {
        this.expectedHardeningColumnCount = expectedHardeningColumnCount;
    }

    public int getExpectedHardeningConstraintCount() {
        return expectedHardeningConstraintCount;
    }

    public void setExpectedHardeningConstraintCount(int expectedHardeningConstraintCount) {
        this.expectedHardeningConstraintCount = expectedHardeningConstraintCount;
    }

    public int getExpectedSchoolScopedSectionRuleCount() {
        return expectedSchoolScopedSectionRuleCount;
    }

    public void setExpectedSchoolScopedSectionRuleCount(int expectedSchoolScopedSectionRuleCount) {
        this.expectedSchoolScopedSectionRuleCount = expectedSchoolScopedSectionRuleCount;
    }

    public int getExpectedAcademicCalendarColumnCount() {
        return expectedAcademicCalendarColumnCount;
    }

    public void setExpectedAcademicCalendarColumnCount(int expectedAcademicCalendarColumnCount) {
        this.expectedAcademicCalendarColumnCount = expectedAcademicCalendarColumnCount;
    }

    public int getExpectedAcademicCalendarConstraintCount() {
        return expectedAcademicCalendarConstraintCount;
    }

    public void setExpectedAcademicCalendarConstraintCount(int expectedAcademicCalendarConstraintCount) {
        this.expectedAcademicCalendarConstraintCount = expectedAcademicCalendarConstraintCount;
    }

    public int getExpectedPaperSizeCount() {
        return expectedPaperSizeCount;
    }

    public void setExpectedPaperSizeCount(int expectedPaperSizeCount) {
        this.expectedPaperSizeCount = expectedPaperSizeCount;
    }

    public int getExpectedPaperSizeSeedCount() {
        return expectedPaperSizeSeedCount;
    }

    public void setExpectedPaperSizeSeedCount(int expectedPaperSizeSeedCount) {
        this.expectedPaperSizeSeedCount = expectedPaperSizeSeedCount;
    }

    public int getExpectedValidatedTemplateRegionCount() {
        return expectedValidatedTemplateRegionCount;
    }

    public void setExpectedValidatedTemplateRegionCount(int expectedValidatedTemplateRegionCount) {
        this.expectedValidatedTemplateRegionCount = expectedValidatedTemplateRegionCount;
    }

    public int getExpectedQuestionTypeCount() {
        return expectedQuestionTypeCount;
    }

    public void setExpectedQuestionTypeCount(int expectedQuestionTypeCount) {
        this.expectedQuestionTypeCount = expectedQuestionTypeCount;
    }

    public int getExpectedOmrTemplateCount() {
        return expectedOmrTemplateCount;
    }

    public void setExpectedOmrTemplateCount(int expectedOmrTemplateCount) {
        this.expectedOmrTemplateCount = expectedOmrTemplateCount;
    }

    public int getExpectedApprovedOmrTemplateCount() {
        return expectedApprovedOmrTemplateCount;
    }

    public void setExpectedApprovedOmrTemplateCount(int expectedApprovedOmrTemplateCount) {
        this.expectedApprovedOmrTemplateCount = expectedApprovedOmrTemplateCount;
    }

    public int getExpectedUnapprovedOmrTemplateCount() {
        return expectedUnapprovedOmrTemplateCount;
    }

    public void setExpectedUnapprovedOmrTemplateCount(int expectedUnapprovedOmrTemplateCount) {
        this.expectedUnapprovedOmrTemplateCount = expectedUnapprovedOmrTemplateCount;
    }

    public int getExpectedPerformanceRuleSetCount() {
        return expectedPerformanceRuleSetCount;
    }

    public void setExpectedPerformanceRuleSetCount(int expectedPerformanceRuleSetCount) {
        this.expectedPerformanceRuleSetCount = expectedPerformanceRuleSetCount;
    }

    public boolean isValidateOnStartup() {
        return validateOnStartup;
    }

    public void setValidateOnStartup(boolean validateOnStartup) {
        this.validateOnStartup = validateOnStartup;
    }
}
