create table users (
    id            uuid primary key,
    email         varchar(254) not null,
    password_hash varchar(100) not null,
    role          varchar(20)  not null,
    created_at    timestamp with time zone not null,
    constraint users_email_unique unique (email),
    constraint users_role_check check (role in ('USER', 'ADMIN'))
);

-- Only the SHA-256 hash of a refresh token is stored: a database leak
-- does not give an attacker usable tokens.
create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid not null references users (id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    constraint refresh_tokens_hash_unique unique (token_hash)
);

create index refresh_tokens_user_id_idx on refresh_tokens (user_id);
