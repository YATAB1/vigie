-- Reporting views for Power BI or any SQL client.
--
-- A repeatable migration: Flyway re-runs this file whenever it changes, after
-- the versioned migrations. Views are dropped and recreated so a column can be
-- added or renamed without a separate migration.
--
-- Everything lives in its own schema so a reporting account can be given read
-- access to it, and to nothing else (see README, "Analyse des données").

create schema if not exists analytics;

drop view if exists analytics.daily_summary;
drop view if exists analytics.check_failure_rates;
drop view if exists analytics.domain_latest;
drop view if exists analytics.check_results;
drop view if exists analytics.scan_facts;

-- One row per finished scan.
create view analytics.scan_facts as
select
    s.id                                                   as scan_id,
    d.id                                                   as domain_id,
    d.hostname,
    d.user_id,
    s.status,
    s.score,
    s.grade,
    s.created_at,
    s.finished_at,
    cast(s.created_at as date)                             as scan_date,
    extract(epoch from (s.finished_at - s.created_at))     as duration_seconds
from scans s
join domains d on d.id = s.domain_id
where s.status in ('SUCCEEDED', 'FAILED');

-- One row per individual check of each successful scan, unpacked from the JSON report.
create view analytics.check_results as
select
    s.id                         as scan_id,
    s.domain_id,
    d.hostname,
    s.created_at,
    c ->> 'id'                   as check_id,
    c ->> 'category'             as category,
    c ->> 'title'                as title,
    c ->> 'status'               as status,
    c ->> 'severity'             as severity
from scans s
join domains d on d.id = s.domain_id
cross join lateral jsonb_array_elements(s.checks::jsonb) as c
where s.status = 'SUCCEEDED'
  and s.checks is not null;

-- Each domain's latest grade, with the previous score to show the trend.
create view analytics.domain_latest as
with ranked as (
    select
        s.domain_id,
        s.id          as scan_id,
        s.score,
        s.grade,
        s.created_at,
        row_number() over (partition by s.domain_id order by s.created_at desc) as recency,
        lag(s.score)  over (partition by s.domain_id order by s.created_at)      as previous_score
    from scans s
    where s.status = 'SUCCEEDED'
)
select
    d.id                          as domain_id,
    d.hostname,
    d.user_id,
    r.scan_id,
    r.score,
    r.grade,
    r.previous_score,
    r.score - r.previous_score    as score_change,
    r.created_at                  as scanned_at
from ranked r
join domains d on d.id = r.domain_id
where r.recency = 1;

-- For each check: on how many domains it currently fails, based on their latest scan.
create view analytics.check_failure_rates as
with latest as (
    select scan_id from analytics.domain_latest
),
latest_checks as (
    select cr.*
    from analytics.check_results cr
    join latest l on l.scan_id = cr.scan_id
)
select
    check_id,
    min(title)                                                     as title,
    min(category)                                                  as category,
    count(*)                                                       as domains_checked,
    count(*) filter (where status = 'fail')                        as domains_failing,
    round(100.0 * count(*) filter (where status = 'fail') / count(*), 1) as failure_rate_pct
from latest_checks
group by check_id;

-- Activity and average score per day.
create view analytics.daily_summary as
select
    scan_date,
    count(*)                                        as scans,
    count(*) filter (where status = 'SUCCEEDED')    as succeeded,
    count(*) filter (where status = 'FAILED')       as failed,
    round(avg(score), 1)                            as average_score,
    round(avg(duration_seconds)::numeric, 1)        as average_duration_seconds
from analytics.scan_facts
group by scan_date;
