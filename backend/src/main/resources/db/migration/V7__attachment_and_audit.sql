create table attachment (
    id uuid primary key,
    household_id uuid not null references household(id),
    owner_type varchar(24) not null check (owner_type in ('ITEM_DEFINITION','INVENTORY_ENTRY')),
    owner_id uuid not null,
    purpose varchar(24) not null check (purpose in ('COVER_IMAGE','ITEM_IMAGE','INVOICE','WARRANTY')),
    original_filename varchar(255) not null,
    storage_key varchar(80) not null unique,
    detected_media_type varchar(80) not null,
    size_bytes bigint not null check (size_bytes between 1 and 20971520),
    sha256 char(64) not null,
    status varchar(16) not null check (status in ('STAGED','AVAILABLE','MISSING','DELETED')),
    uploaded_by uuid not null references user_account(id),
    request_id varchar(80) not null,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    deleted_at timestamptz
);

create unique index uq_attachment_primary_cover
    on attachment(household_id, owner_type, owner_id)
    where purpose = 'COVER_IMAGE' and status = 'AVAILABLE';
create index idx_attachment_owner
    on attachment(household_id, owner_type, owner_id, created_at desc, id desc);
create index idx_attachment_sha256 on attachment(household_id, sha256);
create index idx_attachment_status on attachment(status, created_at, id);

alter table audit_log add column household_id uuid references household(id);
create index idx_audit_household_time on audit_log(household_id, occurred_at desc, id desc);
create index idx_audit_household_actor on audit_log(household_id, actor_account_id, occurred_at desc, id desc);
create index idx_audit_household_event on audit_log(household_id, event_type, occurred_at desc, id desc);
create index idx_audit_household_request on audit_log(household_id, request_id, occurred_at desc, id desc);
