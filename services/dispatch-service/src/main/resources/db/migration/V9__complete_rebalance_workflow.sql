ALTER TABLE dispatch_rebalances
    ADD COLUMN completed_at TIMESTAMPTZ,
    DROP CONSTRAINT dispatch_rebalances_status_check;

UPDATE dispatch_rebalances rebalance
SET status = 'COMPLETED',
    completed_at = COALESCE(dispatch.last_rebalance_at, rebalance.started_at)
FROM dispatches dispatch
WHERE dispatch.id = rebalance.dispatch_id
  AND dispatch.organization_id = rebalance.organization_id
  AND dispatch.status = 'ACTIVE'
  AND rebalance.status = 'COMMANDING'
  AND NOT EXISTS (
      SELECT 1 FROM device_commands command
      WHERE command.dispatch_id = rebalance.dispatch_id
        AND command.status <> 'ACCEPTED'
  );

ALTER TABLE dispatch_rebalances
    ADD CONSTRAINT dispatch_rebalances_status_check CHECK (
        status IN ('COMMANDING', 'COMPLETED', 'FAILED')
    ),
    ADD CONSTRAINT dispatch_rebalances_completion_check CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    );
