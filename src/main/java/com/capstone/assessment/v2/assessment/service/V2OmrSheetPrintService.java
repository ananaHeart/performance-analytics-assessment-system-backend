package com.capstone.assessment.v2.assessment.service;

import com.capstone.assessment.v2.assessment.dto.V2AssessmentPartResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentQuestionResponse;
import com.capstone.assessment.v2.assessment.dto.V2AssessmentResponse;
import com.capstone.assessment.v2.auth.exception.V2AuthException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Profile("v2")
@Service
public class V2OmrSheetPrintService {

    private static final String TEMPLATE_VERSION = "OMR-A4-10-MC-CTX-V2";
    private static final int TEMPLATE_ITEM_COUNT = 10;
    private static final List<String> OPTIONS = List.of("A", "B", "C", "D", "E");

    public String renderSheet(V2AssessmentResponse assessment, Long classListId) {
        validatePrintableAssessment(assessment);
        List<V2AssessmentQuestionResponse> questions = flattenQuestions(assessment);

        String qrPayload = """
                {"v":2,"tv":"%s","t":%d,"cl":%s,"qt":"multiple_choice","n":%d}
                """.formatted(
                TEMPLATE_VERSION,
                assessment.testId(),
                classListId == null ? "null" : classListId,
                TEMPLATE_ITEM_COUNT
        ).trim();

        StringBuilder html = new StringBuilder(16000);
        html.append("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>OMR Answer Sheet</title>
                  <style>
                    @page { size: A4; margin: 12mm; }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      color: #111827;
                      background: #ffffff;
                      font-family: Arial, Helvetica, sans-serif;
                      font-size: 12px;
                    }
                    .sheet {
                      width: 186mm;
                      min-height: 273mm;
                      margin: 0 auto;
                      border: 1.5px solid #111827;
                      padding: 10mm;
                    }
                    .top {
                      display: grid;
                      grid-template-columns: 1fr 42mm;
                      gap: 8mm;
                      align-items: start;
                      border-bottom: 1px solid #111827;
                      padding-bottom: 6mm;
                    }
                    h1 {
                      margin: 0 0 3mm;
                      font-size: 18px;
                      letter-spacing: 0;
                    }
                    .meta {
                      display: grid;
                      grid-template-columns: 28mm 1fr;
                      row-gap: 2mm;
                      column-gap: 3mm;
                    }
                    .label { font-weight: 700; }
                    .line {
                      border-bottom: 1px solid #111827;
                      min-height: 5mm;
                    }
                    .identity {
                      margin-top: 6mm;
                      display: grid;
                      grid-template-columns: 22mm 1fr 22mm 1fr;
                      gap: 3mm;
                      align-items: end;
                    }
                    .qr {
                      border: 1.5px solid #111827;
                      min-height: 42mm;
                      padding: 3mm;
                      font-size: 8px;
                      word-break: break-all;
                    }
                    .qr-title {
                      font-weight: 700;
                      font-size: 10px;
                      margin-bottom: 2mm;
                    }
                    .markers {
                      display: grid;
                      grid-template-columns: repeat(4, 1fr);
                      margin: 5mm 0;
                    }
                    .marker {
                      width: 8mm;
                      height: 8mm;
                      border: 2px solid #111827;
                    }
                    .marker:nth-child(2),
                    .marker:nth-child(4) {
                      justify-self: end;
                    }
                    .grid {
                      width: 100%;
                      border-collapse: collapse;
                      table-layout: fixed;
                    }
                    .grid th,
                    .grid td {
                      border: 1px solid #111827;
                      height: 12mm;
                      text-align: center;
                      vertical-align: middle;
                    }
                    .grid th {
                      height: 8mm;
                      font-size: 11px;
                    }
                    .item {
                      width: 18mm;
                      font-weight: 700;
                    }
                    .qid {
                      display: block;
                      margin-top: 1mm;
                      color: #4b5563;
                      font-size: 7px;
                      font-weight: 400;
                    }
                    .bubble {
                      display: inline-flex;
                      width: 8.5mm;
                      height: 8.5mm;
                      border: 1.6px solid #111827;
                      border-radius: 50%;
                      align-items: center;
                      justify-content: center;
                      font-size: 9px;
                      font-weight: 700;
                    }
                    .note {
                      margin-top: 5mm;
                      font-size: 10px;
                    }
                    @media print {
                      body { background: #ffffff; }
                      .sheet { margin: 0; }
                    }
                  </style>
                </head>
                <body>
                  <main class="sheet">
                    <section class="top">
                      <div>
                """);
        html.append("<h1>").append(escape(assessment.testName())).append("</h1>");
        html.append("<div class=\"meta\">");
        metadata(html, "Template", TEMPLATE_VERSION);
        metadata(html, "Test ID", assessment.testId());
        metadata(html, "Class", "%s - %s".formatted(assessment.gradeLevelName(), assessment.sectionName()));
        metadata(html, "Subject", assessment.subjectName());
        metadata(html, "Term", assessment.termName());
        metadata(html, "Date", assessment.testDate());
        html.append("</div>");
        html.append("""
                        <div class="identity">
                          <span class="label">Name</span><span class="line"></span>
                          <span class="label">ID/LRN</span><span class="line"></span>
                          <span class="label">ClassList</span><span class="line">
                """);
        html.append(classListId == null ? "" : escape(classListId));
        html.append("""
                          </span>
                          <span class="label">Score</span><span class="line"></span>
                        </div>
                      </div>
                      <aside class="qr">
                        <div class="qr-title">QR Payload Placeholder</div>
                """);
        html.append(escape(qrPayload));
        html.append("""
                      </aside>
                    </section>
                    <section class="markers" aria-hidden="true">
                      <div class="marker"></div>
                      <div class="marker"></div>
                      <div class="marker"></div>
                      <div class="marker"></div>
                    </section>
                    <table class="grid">
                      <thead>
                        <tr>
                          <th class="item">Item</th>
                """);
        for (String option : OPTIONS) {
            html.append("<th>").append(option).append("</th>");
        }
        html.append("""
                        </tr>
                      </thead>
                      <tbody>
                """);
        for (int i = 0; i < questions.size(); i++) {
            V2AssessmentQuestionResponse question = questions.get(i);
            html.append("<tr data-question-id=\"").append(question.questionId()).append("\">");
            html.append("<td class=\"item\">").append(i + 1)
                    .append("<span class=\"qid\">QID ")
                    .append(question.questionId())
                    .append("</span></td>");
            for (String option : OPTIONS) {
                html.append("<td><span class=\"bubble\">")
                        .append(option)
                        .append("</span></td>");
            }
            html.append("</tr>");
        }
        html.append("""
                      </tbody>
                    </table>
                    <p class="note">Shade one bubble only. Blank, multiple, or unclear marks will be held for teacher verification.</p>
                  </main>
                </body>
                </html>
                """);
        return html.toString();
    }

    private void validatePrintableAssessment(V2AssessmentResponse assessment) {
        if (assessment == null) {
            throw badRequest("ASSESSMENT_REQUIRED", "Assessment is required.");
        }
        if (!"active".equalsIgnoreCase(assessment.status())) {
            throw badRequest("ASSESSMENT_NOT_ACTIVE", "Only active assessments can be printed as OMR sheets.");
        }
        List<V2AssessmentQuestionResponse> questions = flattenQuestions(assessment);
        if (questions.size() != TEMPLATE_ITEM_COUNT) {
            throw badRequest(
                    "UNSUPPORTED_OMR_ITEM_COUNT",
                    "The current OMR print template supports exactly 10 multiple-choice items."
            );
        }
        for (V2AssessmentPartResponse part : safeParts(assessment)) {
            if (!"multiple_choice".equalsIgnoreCase(part.partType())) {
                throw badRequest(
                        "UNSUPPORTED_OMR_PART_TYPE",
                        "The current OMR print template supports multiple_choice parts only."
                );
            }
        }
    }

    private List<V2AssessmentQuestionResponse> flattenQuestions(V2AssessmentResponse assessment) {
        List<V2AssessmentQuestionResponse> questions = new ArrayList<>();
        for (V2AssessmentPartResponse part : safeParts(assessment)) {
            if (part.questions() != null) {
                questions.addAll(part.questions());
            }
        }
        return questions;
    }

    private List<V2AssessmentPartResponse> safeParts(V2AssessmentResponse assessment) {
        return assessment.parts() == null ? List.of() : assessment.parts();
    }

    private void metadata(StringBuilder html, String label, Object value) {
        html.append("<span class=\"label\">")
                .append(escape(label))
                .append("</span><span>")
                .append(escape(value))
                .append("</span>");
    }

    private V2AuthException badRequest(String code, String message) {
        return new V2AuthException(code, message, HttpStatus.BAD_REQUEST);
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
