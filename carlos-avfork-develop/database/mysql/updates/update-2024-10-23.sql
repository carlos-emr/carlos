CREATE INDEX idx_measurements_demographic_date ON measurements (demographicNo, dateObserved);
CREATE INDEX idx_measurements_type_demographic ON measurements (type, demographicNo);