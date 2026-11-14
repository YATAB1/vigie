create table domains (
    id                   uuid primary key,
    user_id              uuid not null references users (id) on delete cascade,
    hostname             varchar(253) not null,
    status               varchar(20)  not null,
    verification_token   varchar(64)  not null,
    verification_job_id  uuid,
    verification_message varchar(300),
    verified_at          timestamp with time zone,
    created_at           timestamp with time zone not null,
    -- Two users may both claim a hostname; each must prove ownership separately.
    constraint domains_user_hostname_unique unique (user_id, hostname),
    constraint domains_status_check check (status in ('PENDING', 'VERIFIED'))
);

create index domains_verification_job_idx on domains (verification_job_id);

create table scans (
    id          uuid primary key,
    domain_id   uuid not null references domains (id) on delete cascade,
    status      varchar(20) not null,
    score       integer,
    grade       varchar(2),
    checks      text,
    error       varchar(500),
    created_at  timestamp with time zone not null,
    finished_at timestamp with time zone,
    constraint scans_status_check check (status in ('QUEUED', 'SUCCEEDED', 'FAILED')),
    constraint scans_score_check check (score is null or (score >= 0 and score <= 100))
);

create index scans_domain_created_idx on scans (domain_id, created_at);
