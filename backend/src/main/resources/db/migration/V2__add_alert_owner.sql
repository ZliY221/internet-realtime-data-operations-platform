ALTER TABLE alerts ADD COLUMN owner VARCHAR(80);

CREATE INDEX idx_alerts_owner_status ON alerts (owner, status);
