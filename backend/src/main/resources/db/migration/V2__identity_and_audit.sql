-- Identity and audit schema

create table household (
    id uuid primary key,
    singleton_key smallint not null default 1 check (singleton_key = 1),
    name varchar(120) not null,
    timezone varchar(80) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint household_singleton unique (singleton_key)
);

create table user_account (
    id uuid primary key,
    username varchar(64) not null,
    normalized_username varchar(64) not null unique,
    display_name varchar(120) not null,
    email varchar(254),
    password_hash varchar(255) not null,
    status varchar(16) not null check (status in ('ACTIVE', 'DISABLED')),
    must_change_password boolean not null default false,
    credentials_changed_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table household_member (
    id uuid primary key,
    household_id uuid not null references household(id),
    account_id uuid not null references user_account(id),
    role varchar(16) not null check (role in ('ADMIN', 'MEMBER', 'VIEWER')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint household_member_account unique (household_id, account_id)
);

create table member_invite (
    id uuid primary key,
    household_id uuid not null references household(id),
    token_hash char(64) not null unique,
    role varchar(16) not null check (role in ('ADMIN', 'MEMBER', 'VIEWER')),
    expires_at timestamptz not null,
    accepted_at timestamptz,
    revoked_at timestamptz,
    created_by uuid not null references user_account(id),
    accepted_by uuid references user_account(id),
    created_at timestamptz not null default now(),
    check (accepted_at is null or revoked_at is null)
);

create table user_session (
    id uuid primary key,
    account_id uuid not null references user_account(id),
    token_hash char(64) not null unique,
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    idle_expires_at timestamptz not null,
    absolute_expires_at timestamptz not null,
    revoked_at timestamptz,
    revoke_reason varchar(40),
    user_agent varchar(255),
    source_address varchar(64)
);

create index user_session_active_account_idx
    on user_session(account_id, absolute_expires_at) where revoked_at is null;

create index member_invite_active_idx
    on member_invite(household_id, expires_at) where accepted_at is null and revoked_at is null;

create table audit_log (
    id uuid primary key,
    occurred_at timestamptz not null,
    event_type varchar(80) not null,
    outcome varchar(16) not null,
    actor_account_id uuid,
    subject_type varchar(40) not null,
    subject_id uuid,
    request_id varchar(80),
    source varchar(40) not null,
    details jsonb not null default '{}'::jsonb
);

create index audit_log_occurred_at_idx on audit_log(occurred_at desc);
create index audit_log_subject_idx on audit_log(subject_type, subject_id, occurred_at desc);
