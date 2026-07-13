-- Invite enhancements: max uses, use count, and invite tracking on member

ALTER TABLE member_invite ADD COLUMN max_uses INTEGER NOT NULL DEFAULT 1;
ALTER TABLE member_invite ADD COLUMN use_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE household_member ADD COLUMN joined_via_invite_id UUID REFERENCES member_invite(id);

-- Update the availability check to include use_count
DROP INDEX IF EXISTS member_invite_active_idx;
CREATE INDEX member_invite_active_idx
    ON member_invite(household_id, expires_at)
    WHERE accepted_at IS NULL AND revoked_at IS NULL AND use_count < max_uses;
