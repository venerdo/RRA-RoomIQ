ALTER TABLE meeting_share_link
    ADD COLUMN timezone VARCHAR(64);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM meeting_share_link WHERE timezone IS NULL) THEN
        RAISE EXCEPTION 'Existing meeting share links require an authoritative Organization timezone before S6-11';
    END IF;
END
$$;

ALTER TABLE meeting_share_link
    ALTER COLUMN timezone SET NOT NULL;
