package com.atheryon.mortgages;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // Verifies the Spring context starts with Testcontainers Postgres + Flyway
    }

    @Test
    void flywayRanAllNineMigrations() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(9);
    }

    @Test
    void allExpectedTablesExist() {
        List<String> expectedTables = List.of(
                "lixi_messages",
                "domain_events",
                "lender_profiles",
                "webhook_subscriptions",
                "migration_jobs",
                "migration_loan_staging",
                "migration_field_mappings",
                "remediation_actions",
                "reconciliation_reports"
        );

        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(actualTables).containsAll(expectedTables);
    }
}
