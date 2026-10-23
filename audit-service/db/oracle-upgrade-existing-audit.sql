-- Existing installations only: run once as the owner of AUD_AUDIT_EVENTS.
-- For a new installation use oracle-audit-schema.sql instead.
ALTER TABLE AUD_AUDIT_EVENTS ADD (PAYMENT_ID NUMBER(19));
CREATE INDEX IX_AUD_PAYMENT ON AUD_AUDIT_EVENTS (PAYMENT_ID);

-- Existing numeric PAYMENT / PAYMENT_REQUEST rows remain readable without a backfill.
CREATE OR REPLACE TRIGGER AUD_EVENTS_APPEND_ONLY
BEFORE UPDATE OR DELETE ON AUD_AUDIT_EVENTS
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'Audit events are append-only');
END;
/
