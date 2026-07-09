package com.capstone.assessment.importexport.controller;

import com.capstone.assessment.common.response.ApiResponse;
import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.importexport.dto.Sf1ImportSummaryResponse;
import com.capstone.assessment.importexport.service.Sf1ImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import/sf1")
public class Sf1ImportController {

    private final Sf1ImportService sf1ImportService;

    public Sf1ImportController(Sf1ImportService sf1ImportService) {
        this.sf1ImportService = sf1ImportService;
    }

    @PostMapping("/preview")
    public ApiResponse<Sf1ImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        Sf1ImportPreviewResponse response = sf1ImportService.generatePreview(file);
        return ApiResponse.success("SF1 preview generated successfully.", response);
    }

    @PostMapping("/confirm")
    public ApiResponse<Sf1ImportSummaryResponse> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sectionId", required = false) Long sectionId,
            @RequestParam(value = "academicYearId", required = false) Long academicYearId
    ) {
        Sf1ImportSummaryResponse response = sf1ImportService.confirmImport(file, sectionId, academicYearId);
        return ApiResponse.success("SF1 import completed successfully.", response);
    }
}
