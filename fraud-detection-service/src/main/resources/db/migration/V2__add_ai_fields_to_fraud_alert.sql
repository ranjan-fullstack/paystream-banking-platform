ALTER TABLE fraud_alerts ADD COLUMN ai_explanation TEXT;
ALTER TABLE fraud_alerts ADD COLUMN ai_risk_score REAL;
ALTER TABLE fraud_alerts ADD COLUMN detection_method VARCHAR(255);
