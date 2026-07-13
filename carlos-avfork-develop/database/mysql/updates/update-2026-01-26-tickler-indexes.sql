-- Performance indexes for tickler queries
-- These indexes support common filter operations and batch fetching

-- Index for batch fetching tickler links
CREATE INDEX IF NOT EXISTS `idx_tickler_link_tickler_no` ON `tickler_link` (`tickler_no`);

-- Index for composite key
CREATE INDEX IF NOT EXISTS `idx_tickler_status_service_date` ON `tickler` (`status`, `service_date`);

-- Index for "assigned to" filter queries
CREATE INDEX IF NOT EXISTS `idx_tickler_task_assigned_to` ON `tickler` (`task_assigned_to`);

-- Index for "created by" filter queries
CREATE INDEX IF NOT EXISTS `idx_tickler_creator` ON `tickler` (`creator`);
