package com.atheryon.mortgages.lixi;

import com.atheryon.mortgages.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class LixiValidationIT extends AbstractIntegrationTest {

    private static JsonNode calMessage;

    @BeforeAll
    static void loadSampleMessage() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        calMessage = mapper.readTree(
                new ClassPathResource("lixi/samples/cal-valid-complete.json").getInputStream());
    }

    @Test
    void sampleMessageHasTwoApplicants() {
        JsonNode applicants = calMessage
                .path("Package")
                .path("Content")
                .path("Application")
                .path("PersonApplicant");

        assertThat(applicants.isArray()).isTrue();
        assertThat(applicants.size()).isEqualTo(2);
    }

    @Test
    void loanAmountIs680000() {
        JsonNode loanAmount = calMessage
                .path("Package")
                .path("Content")
                .path("Application")
                .path("LoanDetails")
                .path("LoanAmount");

        assertThat(loanAmount.isMissingNode()).isFalse();
        assertThat(loanAmount.asInt()).isEqualTo(680000);
    }
}
