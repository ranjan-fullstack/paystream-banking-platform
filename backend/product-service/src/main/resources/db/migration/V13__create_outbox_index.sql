
-- Polling NEW events fast
CREATE INDEX idx_outbox_status
ON outbox_events (status);

-- Useful for ordering & cleanup
CREATE INDEX idx_outbox_created_at
ON outbox_events (created_at);

-- Optional (debugging / replay support)
CREATE INDEX idx_outbox_aggregate
ON outbox_events (aggregate_type, aggregate_id);
