"""DNS TXT lookups."""

from collections.abc import Callable

import dns.exception
import dns.resolver

TxtLookup = Callable[[str], list[str]]

_MAX_RECORDS = 50
_MAX_RECORD_LENGTH = 1000


class DnsLookupError(Exception):
    """The lookup itself failed (timeout, no reachable server)."""


def lookup_txt(
    name: str, *, lifetime: float = 5.0, resolver: dns.resolver.Resolver | None = None
) -> list[str]:
    """Returns the TXT records of ``name``; an absent name or record type is an empty list."""
    resolver = resolver or dns.resolver.get_default_resolver()
    try:
        answer = resolver.resolve(name, "TXT", lifetime=lifetime)
    except (dns.resolver.NXDOMAIN, dns.resolver.NoAnswer):
        return []
    except dns.exception.DNSException as exc:
        raise DnsLookupError(f"recherche DNS impossible pour {name}") from exc
    records = []
    for rdata in list(answer)[:_MAX_RECORDS]:
        # A TXT record is a sequence of byte strings that must be concatenated.
        text = b"".join(rdata.strings).decode("utf-8", errors="replace")
        records.append(text[:_MAX_RECORD_LENGTH])
    return records
