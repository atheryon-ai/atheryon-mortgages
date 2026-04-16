-- Test-only schema bootstrap.
--
-- Hibernate (ddl-auto=create-drop) generates all tables from JPA metadata, but
-- it does NOT know about the application_number_seq sequence used by
-- ApplicationService. With spring.jpa.defer-datasource-initialization=true
-- Spring runs this script AFTER Hibernate, so the sequence is created on
-- whatever schema Hibernate just produced.
CREATE SEQUENCE IF NOT EXISTS application_number_seq START WITH 1 INCREMENT BY 1;
