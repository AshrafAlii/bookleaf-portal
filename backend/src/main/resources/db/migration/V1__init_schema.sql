-- BookLeaf Author Support Portal — initial schema.
-- Flyway owns the schema; Hibernate runs with ddl-auto=validate against it.

create table app_user (
    id            uuid primary key default gen_random_uuid(),
    email         varchar(255) not null unique,
    password_hash varchar(255) not null,
    full_name     varchar(255) not null,
    role          varchar(20)  not null,
    active        boolean      not null default true,
    created_at    timestamptz  not null default now(),
    last_login_at timestamptz
);

create table author (
    id          uuid primary key default gen_random_uuid(),
    author_code varchar(20)  not null unique,
    user_id     uuid          not null unique references app_user (id) on delete cascade,
    name        varchar(255) not null,
    email       varchar(255) not null unique,
    phone       varchar(40),
    city        varchar(120),
    joined_date date
);

create table book (
    id                       uuid primary key default gen_random_uuid(),
    book_code                varchar(20)        not null unique,
    author_id                uuid              not null references author (id) on delete cascade,
    title                    varchar(255)       not null,
    isbn                     varchar(40)        not null,
    genre                    varchar(120),
    publication_date         date,
    status                   varchar(60)        not null,
    mrp                      numeric(10, 2),
    author_royalty_per_copy  numeric(10, 2),
    total_copies_sold        integer            not null default 0,
    total_royalty_earned     numeric(14, 2)     not null default 0,
    royalty_paid             numeric(14, 2)     not null default 0,
    royalty_pending          numeric(14, 2)     not null default 0,
    last_royalty_payout_date date,
    print_partner            varchar(120)
);

create table book_platform (
    id       bigserial primary key,
    book_id  uuid        not null references book (id) on delete cascade,
    platform varchar(80) not null,
    constraint uk_book_platform unique (book_id, platform)
);

create table ticket (
    id                 uuid primary key default gen_random_uuid(),
    ticket_code        varchar(30)   not null unique,
    author_id          uuid          not null references author (id) on delete cascade,
    book_id            uuid          references book (id) on delete set null,
    subject            varchar(255)  not null,
    description        text          not null,
    attachment_name    varchar(255),
    status             varchar(30)   not null,
    category           varchar(40)   not null,
    category_source    varchar(20)   not null,
    priority           varchar(20)   not null,
    priority_source    varchar(20)   not null,
    priority_rank      integer        not null default 2,
    priority_rationale varchar(600),
    assigned_to        uuid          references app_user (id) on delete set null,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    first_response_at  timestamptz,
    resolved_at        timestamptz,
    closed_at          timestamptz
);

create table ticket_message (
    id            bigserial primary key,
    ticket_id     uuid       not null references ticket (id) on delete cascade,
    author_user_id uuid      references app_user (id) on delete set null,
    body          text       not null,
    internal      boolean    not null default false,
    created_at    timestamptz not null default now()
);

create table ticket_event (
    id            bigserial primary key,
    ticket_id     uuid       not null references ticket (id) on delete cascade,
    actor_user_id uuid       references app_user (id) on delete set null,
    event_type    varchar(40) not null,
    detail        varchar(1000),
    created_at    timestamptz not null default now()
);

-- Every LLM call is logged so cost/latency can be reviewed in the admin UI.
create table ai_usage_log (
    id                bigserial primary key,
    purpose           varchar(40)  not null,
    provider          varchar(30)  not null,
    model             varchar(60),
    prompt_tokens     integer,
    completion_tokens integer,
    cached            boolean      not null default false,
    latency_ms        integer,
    success           boolean      not null default true,
    error_message     varchar(600),
    created_at        timestamptz  not null default now()
);

-- Content-addressed cache of AI results (same ticket text => same answer, no re-billing).
create table ai_cache_entry (
    id         bigserial primary key,
    cache_key  varchar(160) not null unique,
    purpose    varchar(40)  not null,
    payload    text         not null,
    created_at timestamptz  not null default now()
);

create sequence ticket_code_seq start 1;

create index idx_ticket_status on ticket (status);
create index idx_ticket_author on ticket (author_id);
create index idx_ticket_created on ticket (created_at desc);
create index idx_ticket_book on ticket (book_id);
create index idx_ticket_message_ticket on ticket_message (ticket_id, created_at);
create index idx_book_author on book (author_id);
create index idx_ai_usage_created on ai_usage_log (created_at desc);
