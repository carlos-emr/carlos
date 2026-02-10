-- Test lookup tables for reference data
-- These tables are referenced by formulas in HBM mappings but don't have entity classes

-- Gender lookup table (Hibernate may auto-create this due to formula in Demographic.hbm.xml)
CREATE TABLE IF NOT EXISTS lst_gender (
    code char(1) NOT NULL PRIMARY KEY,
    description varchar(80),
    isactive tinyint(1) DEFAULT 1,
    displayorder int(10)
);

-- Use MERGE for H2 to handle existing data gracefully
MERGE INTO lst_gender (code, description, isactive, displayorder) KEY(code) VALUES
('M', 'Male', 1, 1),
('F', 'Female', 1, 2),
('X', 'Non-binary', 1, 3),
('U', 'Unknown', 1, 4),
('O', 'Other', 1, 5);

-- Demographic merged table (for head record lookup)
CREATE TABLE IF NOT EXISTS demographic_merged (
    id int AUTO_INCREMENT PRIMARY KEY,
    demographic_no int NOT NULL,
    merged_to int,
    deleted tinyint(1) DEFAULT 0,
    created_date datetime DEFAULT CURRENT_TIMESTAMP
);

-- Health safety table (for alert count)
CREATE TABLE IF NOT EXISTS health_safety (
    id int AUTO_INCREMENT PRIMARY KEY,
    demographic_no int NOT NULL,
    description text,
    created_date datetime DEFAULT CURRENT_TIMESTAMP
);

-- Service restriction lookup table (for ProgramClientRestriction formula)
CREATE TABLE IF NOT EXISTS lst_service_restriction (
    id varchar(36) NOT NULL PRIMARY KEY,
    description varchar(255),
    isactive tinyint(1) DEFAULT 1
);

-- Joint admissions table (for ProgramQueue formula)
CREATE TABLE IF NOT EXISTS joint_admissions (
    id int AUTO_INCREMENT PRIMARY KEY,
    client_id int NOT NULL,
    head_client_id int,
    archived tinyint(1) DEFAULT 0,
    created_date datetime DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- Reference data for healthcare domain integration tests
-- =====================================================================

-- Appointment status reference data
-- Note: appointmentStatus table is auto-created by Hibernate from @Entity
-- We insert reference data that tests may depend on
INSERT INTO appointmentStatus (status, description, active, editable, icon, color)
SELECT 't', 'Confirmed', 1, 1, '', '' WHERE NOT EXISTS (SELECT 1 FROM appointmentStatus WHERE status = 't');
INSERT INTO appointmentStatus (status, description, active, editable, icon, color)
SELECT 'N', 'No Show', 1, 1, '', '' WHERE NOT EXISTS (SELECT 1 FROM appointmentStatus WHERE status = 'N');
INSERT INTO appointmentStatus (status, description, active, editable, icon, color)
SELECT 'C', 'Cancelled', 1, 1, '', '' WHERE NOT EXISTS (SELECT 1 FROM appointmentStatus WHERE status = 'C');
INSERT INTO appointmentStatus (status, description, active, editable, icon, color)
SELECT 'H', 'Here', 1, 1, '', '' WHERE NOT EXISTS (SELECT 1 FROM appointmentStatus WHERE status = 'H');

-- Schedule template codes reference data
INSERT INTO scheduletemplatecode (code, description, duration, color, confirm, bookinglimit)
SELECT 'A', 'Available', '15', '00FF00', '', 1 WHERE NOT EXISTS (SELECT 1 FROM scheduletemplatecode WHERE code = 'A');
INSERT INTO scheduletemplatecode (code, description, duration, color, confirm, bookinglimit)
SELECT 'P', 'Primary Care', '15', '0000FF', '', 1 WHERE NOT EXISTS (SELECT 1 FROM scheduletemplatecode WHERE code = 'P');
INSERT INTO scheduletemplatecode (code, description, duration, color, confirm, bookinglimit)
SELECT '-', 'Not Available', '15', 'FF0000', '', 0 WHERE NOT EXISTS (SELECT 1 FROM scheduletemplatecode WHERE code = '-');
