package com.capstone.assessment.importexport.service;

public interface ExportService {

    byte[] exportItemAnalysis(Long testId);

    byte[] exportLmsReport(Long testId);

    byte[] exportStudentScores(Long testId);
}
