-- Update script to remove HealthSafety functionality
-- Date: 2025-08-25
-- Description: Complete removal of health_safety table and related functionality
-- 
-- CAUTION: This will permanently delete all health and safety data
-- Make sure to backup the health_safety table before running if data retention is required
--
-- Uncomment the lines below to execute the removal:

-- Step 1: Drop the health_safety table
-- WARNING: This will permanently delete all health and safety records
-- DROP TABLE IF EXISTS health_safety;

-- Note: The following related functionality has been removed from the codebase:
-- - org.oscarehr.PMmodule.model.HealthSafety (Hibernate model)
-- - org.oscarehr.PMmodule.daos.HealthSafetyDao* (Data access objects)
-- - org.oscarehr.PMmodule.service.HealthSafetyManager* (Service classes)  
-- - org.oscarehr.PMmodule.www.HealthSafety2Action (Struts2 action)
-- - PMmodule/HealthSafety struts configuration
-- - applicationContextCaisi.xml bean definitions for healthSafetyDao and healthSafetyManager

-- Usage statistics (uncomment to check table contents before deletion):
-- SELECT COUNT(*) as 'Total Health Safety Records' FROM health_safety;
-- SELECT demographic_no, COUNT(*) as 'Records per Patient' FROM health_safety GROUP BY demographic_no ORDER BY demographic_no;

-- To re-enable this feature in the future, restore:
-- 1. The health_safety table structure:
--    CREATE TABLE health_safety (
--      id             bigint(20) not null auto_increment,
--      demographic_no bigint(20) default '0' not null,
--      message        text,
--      username       varchar(128),
--      updatedate     datetime,
--      primary key (id)
--    );
-- 2. All Java classes removed by this cleanup
-- 3. Spring bean configurations 
-- 4. Struts configuration