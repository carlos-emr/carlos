-- Coordinate short Messenger admin transactions across application instances.
-- The singleton key protects registry and group membership inserts even when neither
-- exists yet. Existing memberships and clinical data are not rewritten or deleted.
CREATE TABLE IF NOT EXISTS messengerMembershipLock (
    id INT NOT NULL,
    lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',
    lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB;
