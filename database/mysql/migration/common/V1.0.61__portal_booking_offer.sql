-- Patient portal offered appointment times (issue #3850).
--
-- Staff can send a booking prompt with a few open times; the patient picks one in the portal and
-- CARLOS books it. The portal only ever receives an opaque slot_id per time (32 random characters,
-- nothing readable). This table is the only place that says which provider and time a slot_id
-- stands for, and what became of it.
--
-- slot_id: 32 random characters, nothing readable
-- status: offered / booked / unavailable / closed
CREATE TABLE IF NOT EXISTS portal_booking_offer (
  slot_id          VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
  operation_id     VARCHAR(64) NOT NULL,
  prompt_id        BIGINT      DEFAULT NULL,
  demographic_no   INT         NOT NULL,
  provider_no      VARCHAR(6)  NOT NULL,
  start_time       DATETIME    NOT NULL,
  duration_minutes INT         NOT NULL,
  template_code    CHAR(1)     NOT NULL,
  offered_by       VARCHAR(6)  NOT NULL,
  status           VARCHAR(16) NOT NULL,
  choice_id        BIGINT      DEFAULT NULL,
  appointment_no   INT         DEFAULT NULL,
  tickler_no       INT         DEFAULT NULL,
  created_at       DATETIME    NOT NULL,
  updated_at       DATETIME    NOT NULL,
  PRIMARY KEY (slot_id),
  KEY (prompt_id), KEY (operation_id),
  KEY (provider_no, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci;
