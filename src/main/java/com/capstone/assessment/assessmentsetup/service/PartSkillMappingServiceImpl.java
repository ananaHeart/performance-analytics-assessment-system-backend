package com.capstone.assessment.assessmentsetup.service;

import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingEntryDto;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingEntryRequest;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingPreviewResponse;
import com.capstone.assessment.assessmentsetup.dto.PartSkillMappingRequest;
import com.capstone.assessment.assessmentsetup.repository.PartSkillMappingRepository;
import com.capstone.assessment.assessmentsetup.repository.PartSkillMappingRepository.CompetencyContext;
import com.capstone.assessment.assessmentsetup.repository.PartSkillMappingRepository.TestPartMappingContext;
import com.capstone.assessment.common.exception.BadRequestException;
import com.capstone.assessment.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PartSkillMappingServiceImpl implements PartSkillMappingService {

    private static final String RANGE_MODE = "RANGE";

    private final PartSkillMappingRepository partSkillMappingRepository;

    public PartSkillMappingServiceImpl(PartSkillMappingRepository partSkillMappingRepository) {
        this.partSkillMappingRepository = partSkillMappingRepository;
    }

    @Override
    public PartSkillMappingPreviewResponse preview(PartSkillMappingRequest request) {
        ValidatedMapping validatedMapping = validateAndNormalize(request);
        return toPreviewResponse(validatedMapping, null);
    }

    @Override
    @Transactional
    public PartSkillMappingPreviewResponse save(PartSkillMappingRequest request) {
        ValidatedMapping validatedMapping = validateAndNormalize(request);

        partSkillMappingRepository.deleteByTestPartId(validatedMapping.context().testPartId());

        List<PartSkillMappingEntryDto> savedMappings = new ArrayList<>();
        for (PartSkillMappingEntryDto mapping : validatedMapping.mappings()) {
            Long mappingId = partSkillMappingRepository.insertMapping(
                    mapping.testPartId(),
                    mapping.competencyId(),
                    mapping.itemCount(),
                    mapping.startItem(),
                    mapping.endItem()
            );

            savedMappings.add(new PartSkillMappingEntryDto(
                    mappingId,
                    mapping.testPartId(),
                    mapping.competencyId(),
                    mapping.competencyName(),
                    mapping.mappingMode(),
                    mapping.itemCount(),
                    mapping.startItem(),
                    mapping.endItem(),
                    mapping.itemNumbers()
            ));
        }

        return toPreviewResponse(validatedMapping, savedMappings);
    }

    @Override
    public List<PartSkillMappingEntryDto> getByTestPart(Long testPartId) {
        if (testPartId == null) {
            throw new BadRequestException("Test part ID is required.");
        }

        partSkillMappingRepository.findTestPartContext(testPartId)
                .orElseThrow(() -> new ResourceNotFoundException("Test part not found."));

        return partSkillMappingRepository.findMappingsByTestPartId(testPartId);
    }

    private ValidatedMapping validateAndNormalize(PartSkillMappingRequest request) {
        if (request == null) {
            throw new BadRequestException("Part skill mapping request must not be null.");
        }

        if (request.testPartId() == null) {
            throw new BadRequestException("Test part ID is required.");
        }

        if (request.mappings() == null || request.mappings().isEmpty()) {
            throw new BadRequestException("At least one branch skill mapping is required.");
        }

        TestPartMappingContext context = partSkillMappingRepository.findTestPartContext(request.testPartId())
                .orElseThrow(() -> new ResourceNotFoundException("Test part not found."));

        if (context.selectedParentId() != null) {
            throw new BadRequestException("Test part competency must be a parent competency.");
        }

        List<PartSkillMappingEntryDto> normalizedMappings = new ArrayList<>();
        Set<Long> usedCompetencyIds = new HashSet<>();
        Set<Integer> coveredItems = new HashSet<>();
        int nextRangeStart = 1;

        for (PartSkillMappingEntryRequest mappingRequest : request.mappings()) {
            if (mappingRequest == null) {
                throw new BadRequestException("Branch skill mapping must not be null.");
            }

            if (mappingRequest.competencyId() == null) {
                throw new BadRequestException("Branch skill competency ID is required.");
            }

            if (!usedCompetencyIds.add(mappingRequest.competencyId())) {
                throw new BadRequestException("Branch skill cannot be mapped more than once for the same test part.");
            }

            CompetencyContext competency = partSkillMappingRepository.findCompetencyContext(mappingRequest.competencyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch skill competency not found."));

            if (!context.parentCompetencyId().equals(competency.parentCompetencyId())) {
                throw new BadRequestException("Branch skill must belong to the test part parent competency.");
            }

            validateRangeOnly(mappingRequest.mappingMode());
            if (mappingRequest.itemCount() == null || mappingRequest.itemCount() <= 0) {
                throw new BadRequestException("Item count must be greater than 0.");
            }

            PartSkillMappingEntryDto normalizedMapping = normalizeRangeMapping(
                    context,
                    competency,
                    mappingRequest,
                    nextRangeStart,
                    coveredItems
            );
            nextRangeStart = normalizedMapping.endItem() + 1;

            normalizedMappings.add(normalizedMapping);
        }

        if (coveredItems.size() != context.numberOfItems()) {
            throw new BadRequestException("Mapped items must cover all items in the test part.");
        }

        return new ValidatedMapping(context, normalizedMappings, coveredItems.size());
    }

    private PartSkillMappingEntryDto normalizeRangeMapping(
            TestPartMappingContext context,
            CompetencyContext competency,
            PartSkillMappingEntryRequest request,
            int nextRangeStart,
            Set<Integer> coveredItems
    ) {
        Integer startItem = request.startItem();
        Integer endItem = request.endItem();

        if (startItem == null && endItem == null) {
            startItem = nextRangeStart;
            endItem = startItem + request.itemCount() - 1;
        }

        if (startItem == null || endItem == null) {
            throw new BadRequestException("Start item and end item are required for range mapping.");
        }

        if (startItem < 1) {
            throw new BadRequestException("Start item must be at least 1.");
        }

        if (endItem < startItem) {
            throw new BadRequestException("End item must be greater than or equal to start item.");
        }

        if (endItem > context.numberOfItems()) {
            throw new BadRequestException("Mapped range must not exceed the test part number of items.");
        }

        int derivedItemCount = endItem - startItem + 1;
        if (derivedItemCount != request.itemCount()) {
            throw new BadRequestException("Item count must match the generated range size.");
        }

        List<Integer> itemNumbers = new ArrayList<>();
        for (int itemNumber = startItem; itemNumber <= endItem; itemNumber++) {
            if (!coveredItems.add(itemNumber)) {
                throw new BadRequestException("Mapped item ranges must not overlap.");
            }
            itemNumbers.add(itemNumber);
        }

        return new PartSkillMappingEntryDto(
                null,
                context.testPartId(),
                competency.competencyId(),
                competency.competencyName(),
                RANGE_MODE,
                request.itemCount(),
                startItem,
                endItem,
                itemNumbers
        );
    }

    private void validateRangeOnly(String mappingMode) {
        if (mappingMode == null || mappingMode.isBlank()) {
            return;
        }

        if (RANGE_MODE.equalsIgnoreCase(mappingMode.trim())) {
            return;
        }

        throw new BadRequestException("Only range mapping is supported.");
    }

    private PartSkillMappingPreviewResponse toPreviewResponse(
            ValidatedMapping validatedMapping,
            List<PartSkillMappingEntryDto> savedMappings
    ) {
        List<PartSkillMappingEntryDto> mappings = savedMappings == null
                ? validatedMapping.mappings()
                : savedMappings;

        return new PartSkillMappingPreviewResponse(
                validatedMapping.context().testPartId(),
                validatedMapping.context().parentCompetencyId(),
                validatedMapping.context().parentCompetencyName(),
                validatedMapping.context().numberOfItems(),
                validatedMapping.mappedItemCount(),
                validatedMapping.mappedItemCount().equals(validatedMapping.context().numberOfItems()),
                mappings
        );
    }

    private record ValidatedMapping(
            TestPartMappingContext context,
            List<PartSkillMappingEntryDto> mappings,
            Integer mappedItemCount
    ) {
    }
}
