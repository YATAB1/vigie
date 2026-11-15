-- Checks the reporting views on a real PostgreSQL (run by CI after the migrations).
-- Inserts a small known dataset, then fails with an error if any view disagrees.

begin;

insert into users values
    ('00000000-0000-0000-0000-000000000001', 'a@example.com', 'hash', 'USER', now());

insert into domains values
    ('00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-000000000001',
     'alpha.example', 'VERIFIED', 't1', null, null, now(), now()),
    ('00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-000000000001',
     'beta.example', 'VERIFIED', 't2', null, null, now(), now());

insert into scans values
    ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-00000000000a', 'SUCCEEDED', 56, 'C',
     '[{"id":"headers.hsts","category":"headers","title":"HSTS","status":"fail","severity":"high"},
       {"id":"mail.spf","category":"courriel","title":"SPF","status":"fail","severity":"medium"}]',
     null, '2026-11-13 10:00+00', '2026-11-13 10:00:12+00'),
    ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-00000000000a', 'SUCCEEDED', 80, 'B',
     '[{"id":"headers.hsts","category":"headers","title":"HSTS","status":"pass","severity":"info"},
       {"id":"mail.spf","category":"courriel","title":"SPF","status":"fail","severity":"medium"}]',
     null, '2026-11-14 10:00+00', '2026-11-14 10:00:08+00'),
    ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-00000000000b', 'SUCCEEDED', 70, 'C',
     '[{"id":"headers.hsts","category":"headers","title":"HSTS","status":"fail","severity":"high"},
       {"id":"mail.spf","category":"courriel","title":"SPF","status":"pass","severity":"info"}]',
     null, '2026-11-14 11:00+00', '2026-11-14 11:00:10+00'),
    ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-00000000000b', 'FAILED', null, null, null,
     'timeout', '2026-11-14 12:00+00', '2026-11-14 12:05+00'),
    ('00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-00000000000b', 'QUEUED', null, null, null,
     null, '2026-11-14 13:00+00', null);

do $$
declare
    alpha record;
    beta record;
    hsts record;
    day record;
begin
    select * into alpha from analytics.domain_latest where hostname = 'alpha.example';
    if alpha.score <> 80 or alpha.previous_score <> 56 or alpha.score_change <> 24 then
        raise exception 'domain_latest wrong for alpha: %', row_to_json(alpha);
    end if;

    -- The failed and the queued scans must not replace beta's last grade.
    select * into beta from analytics.domain_latest where hostname = 'beta.example';
    if beta.score <> 70 or beta.previous_score is not null then
        raise exception 'domain_latest wrong for beta: %', row_to_json(beta);
    end if;

    select * into hsts from analytics.check_failure_rates where check_id = 'headers.hsts';
    if hsts.domains_checked <> 2 or hsts.domains_failing <> 1 or hsts.failure_rate_pct <> 50.0 then
        raise exception 'check_failure_rates wrong: %', row_to_json(hsts);
    end if;

    if (select count(*) from analytics.check_results) <> 6 then
        raise exception 'check_results should hold 6 rows';
    end if;

    if (select count(*) from analytics.scan_facts) <> 4 then
        raise exception 'scan_facts should exclude the queued scan';
    end if;

    select * into day from analytics.daily_summary where scan_date = date '2026-11-14';
    if day.scans <> 3 or day.succeeded <> 2 or day.failed <> 1 or day.average_score <> 75.0 then
        raise exception 'daily_summary wrong: %', row_to_json(day);
    end if;

    raise notice 'analytics views: all checks passed';
end $$;

rollback;
