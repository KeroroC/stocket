-- Reminder, notification delivery, and Spring Modulith event publication baseline

create table reminder_rule (
    id uuid primary key,
    household_id uuid not null references household(id),
    scope_type varchar(16) not null check (scope_type in ('HOUSEHOLD', 'CATEGORY', 'ITEM')),
    scope_id uuid,
    expiration_offsets integer[] not null default array[30, 7, 1, 0],
    low_stock_threshold numeric(19,4) check (low_stock_threshold >= 0),
    enabled boolean not null default true,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((scope_type = 'HOUSEHOLD' and scope_id is null)
        or (scope_type in ('CATEGORY', 'ITEM') and scope_id is not null)),
    check (cardinality(expiration_offsets) > 0)
);

create unique index uq_reminder_rule_scope
    on reminder_rule (household_id, scope_type,
        coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table reminder (
    id uuid primary key,
    household_id uuid not null references household(id),
    item_definition_id uuid not null references item_definition(id),
    inventory_entry_id uuid references inventory_entry(id),
    reminder_type varchar(24) not null
        check (reminder_type in ('EXPIRING', 'EXPIRED', 'LOW_STOCK', 'INTEGRITY')),
    trigger_key varchar(80) not null,
    trigger_at timestamptz not null,
    status varchar(16) not null
        check (status in ('SCHEDULED', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    opened_at timestamptz,
    resolved_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((status = 'RESOLVED' and resolved_at is not null)
        or (status <> 'RESOLVED' and resolved_at is null))
);

create unique index uq_active_reminder
    on reminder (household_id, item_definition_id,
        coalesce(inventory_entry_id, '00000000-0000-0000-0000-000000000000'::uuid),
        reminder_type, trigger_key)
    where status in ('SCHEDULED', 'OPEN', 'ACKNOWLEDGED');

create index reminder_household_status_trigger_idx
    on reminder (household_id, status, trigger_at, id);

create table notification_channel (
    id uuid primary key,
    household_id uuid not null references household(id),
    type varchar(24) not null check (type in ('IN_APP', 'WEB_PUSH', 'SMTP', 'WEBHOOK')),
    enabled boolean not null default true,
    configuration_json jsonb not null default '{}'::jsonb
        check (jsonb_typeof(configuration_json) = 'object'),
    encrypted_secret text,
    key_version integer,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((encrypted_secret is null and key_version is null)
        or (encrypted_secret is not null and key_version is not null))
);

create index notification_channel_household_type_idx
    on notification_channel (household_id, type, enabled);

create table push_subscription (
    id uuid primary key,
    household_id uuid not null references household(id),
    member_id uuid not null references household_member(id),
    endpoint_hash char(64) not null,
    encrypted_endpoint text not null,
    encrypted_p256dh text not null,
    encrypted_auth text not null,
    key_version integer not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_push_subscription_member_endpoint unique (member_id, endpoint_hash)
);

create index push_subscription_household_enabled_idx
    on push_subscription (household_id, enabled, member_id);

create table notification_delivery (
    id uuid primary key,
    household_id uuid not null references household(id),
    reminder_id uuid not null references reminder(id),
    member_id uuid not null references household_member(id),
    channel_type varchar(24) not null check (channel_type in ('IN_APP', 'WEB_PUSH', 'SMTP', 'WEBHOOK')),
    channel_id uuid references notification_channel(id),
    dedupe_key varchar(180) not null unique,
    status varchar(16) not null
        check (status in ('PENDING', 'PROCESSING', 'DELIVERED', 'RETRY_WAIT', 'DEAD', 'CANCELLED')),
    attempt_count integer not null default 0 check (attempt_count >= 0),
    next_attempt_at timestamptz,
    lease_owner varchar(120),
    lease_until timestamptz,
    last_error_code varchar(80),
    last_error_at timestamptz,
    delivered_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check ((status = 'PROCESSING' and lease_owner is not null and lease_until is not null)
        or (status <> 'PROCESSING' and lease_owner is null and lease_until is null))
);

create index notification_delivery_claim_idx
    on notification_delivery (status, next_attempt_at, id)
    where status in ('PENDING', 'RETRY_WAIT', 'PROCESSING');

create index notification_delivery_household_status_idx
    on notification_delivery (household_id, status, updated_at desc, id desc);

-- Copied verbatim from spring-modulith-events-jdbc 2.0.5:
-- org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql
create table if not exists event_publication
(
    id                     uuid not null,
    listener_id            text not null,
    event_type             text not null,
    serialized_event       text not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    int,
    last_resubmission_date timestamp with time zone,
    primary key (id)
);

create index if not exists event_publication_serialized_event_hash_idx
    on event_publication using hash(serialized_event);
create index if not exists event_publication_by_completion_date_idx
    on event_publication (completion_date);
