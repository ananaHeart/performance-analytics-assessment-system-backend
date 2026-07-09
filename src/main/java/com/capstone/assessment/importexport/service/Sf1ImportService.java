package com.capstone.assessment.importexport.service;

import com.capstone.assessment.importexport.dto.Sf1ImportPreviewResponse;
import com.capstone.assessment.importexport.dto.Sf1ImportSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

public interface Sf1ImportService {

    Sf1ImportPreviewResponse generatePreview(MultipartFile file);

    Sf1ImportSummaryResponse confirmImport(MultipartFile file, Long sectionId, Long academicYearId);
}
