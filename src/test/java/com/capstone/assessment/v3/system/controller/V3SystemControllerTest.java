package com.capstone.assessment.v3.system.controller;

import com.capstone.assessment.v3.system.service.V3DatabaseReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class V3SystemControllerTest {

    @Test
    void pingAnswersWithoutCheckingTheDatabase() {
        V3DatabaseReadinessService readiness = mock(V3DatabaseReadinessService.class);

        var response = new V3SystemController(readiness).ping();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsEntry("status", "up");
        verifyNoInteractions(readiness);
    }
}
