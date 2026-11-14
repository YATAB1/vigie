"""TXT lookups against a real DNS server running locally."""

import socket
import threading

import dns.message
import dns.rcode
import dns.resolver
import dns.rrset
import pytest

from vigie_worker.dns import DnsLookupError, lookup_txt

ZONE = {
    "_vigie-challenge.example.com.": ['"vigie-verification=abc123"'],
    "example.com.": ['"v=spf1 -all"', '"google-site-verification=xyz"'],
    # One record split into two strings, as DNS requires past 255 bytes.
    "long.example.com.": ['"v=spf1 include:a.example.com " "include:b.example.com -all"'],
}


@pytest.fixture
def resolver():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("127.0.0.1", 0))
    running = True

    def serve():
        while running:
            try:
                wire, client = sock.recvfrom(4096)
            except OSError:
                return
            query = dns.message.from_wire(wire)
            name = query.question[0].name
            if name.to_text() == "silent.example.com.":
                continue  # never answers: simulates a timeout
            response = dns.message.make_response(query)
            records = ZONE.get(name.to_text())
            if records is None:
                response.set_rcode(dns.rcode.NXDOMAIN)
            else:
                response.answer.append(dns.rrset.from_text(name, 60, "IN", "TXT", *records))
            sock.sendto(response.to_wire(), client)

    threading.Thread(target=serve, daemon=True).start()
    local = dns.resolver.Resolver(configure=False)
    local.nameservers = ["127.0.0.1"]
    local.port = sock.getsockname()[1]
    yield local
    running = False
    sock.close()


def test_returns_the_txt_record(resolver):
    assert lookup_txt("_vigie-challenge.example.com", resolver=resolver) == [
        "vigie-verification=abc123"
    ]


def test_returns_every_record_of_the_name(resolver):
    assert sorted(lookup_txt("example.com", resolver=resolver)) == [
        "google-site-verification=xyz",
        "v=spf1 -all",
    ]


def test_split_strings_are_joined_into_one_record(resolver):
    assert lookup_txt("long.example.com", resolver=resolver) == [
        "v=spf1 include:a.example.com include:b.example.com -all"
    ]


def test_unknown_name_is_an_empty_answer_not_an_error(resolver):
    assert lookup_txt("missing.example.com", resolver=resolver) == []


def test_timeout_is_reported_as_a_lookup_error(resolver):
    with pytest.raises(DnsLookupError):
        lookup_txt("silent.example.com", lifetime=0.5, resolver=resolver)
