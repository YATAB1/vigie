import pytest

from vigie_worker.hostnames import is_valid_hostname, is_valid_record_name


@pytest.mark.parametrize(
    "value", ["example.com", "sub.example.co.uk", "a-b.example.io", "xn--bcher-kva.example"]
)
def test_valid_hostnames(value):
    assert is_valid_hostname(value)


@pytest.mark.parametrize(
    "value",
    [
        "",
        "localhost",  # single label
        "127.0.0.1",  # IP literal
        "example.com:8080",  # port
        "example.com/admin",  # path
        "http://example.com",  # scheme
        "user@example.com",
        "-bad.example.com",
        "bad-.example.com",
        "exa mple.com",
        "example..com",
        "EXAMPLE.COM",  # must already be lowercased
        "_dmarc.example.com",  # underscore is only for record names
        "a" * 64 + ".com",  # label too long
        "a." * 130 + "com",  # name too long
    ],
)
def test_invalid_hostnames(value):
    assert not is_valid_hostname(value)


def test_record_names_accept_underscore_labels():
    assert is_valid_record_name("_dmarc.example.com")
    assert is_valid_record_name("_vigie-challenge.sub.example.com")
    assert not is_valid_record_name("_dmarc.127.0.0.1")
