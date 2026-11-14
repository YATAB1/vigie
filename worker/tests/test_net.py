import pytest

from vigie_worker.net import (
    TargetNotAllowedError,
    TargetUnresolvableError,
    is_public_address,
    resolve_target,
)


@pytest.mark.parametrize(
    "address",
    [
        "127.0.0.1",  # loopback
        "10.0.0.5",  # private
        "172.16.3.4",  # private
        "192.168.1.1",  # private
        "169.254.169.254",  # link-local: cloud metadata endpoint
        "100.64.0.1",  # carrier-grade NAT
        "0.0.0.0",  # noqa: S104 - unspecified address, used as test data
        "224.0.0.1",  # multicast
        "::1",  # IPv6 loopback
        "fe80::1",  # IPv6 link-local
        "fc00::1",  # IPv6 unique local
        "::ffff:10.0.0.1",  # private IPv4 hidden in an IPv6 address
        "::ffff:127.0.0.1",
    ],
)
def test_internal_addresses_are_not_public(address):
    assert is_public_address(address) is False


@pytest.mark.parametrize("address", ["8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"])
def test_internet_addresses_are_public(address):
    assert is_public_address(address) is True


def resolver_returning(*addresses):
    return lambda _hostname, _port: list(addresses)


def test_public_target_is_accepted():
    assert resolve_target("example.com", 443, resolver=resolver_returning("93.184.216.34")) == (
        "93.184.216.34"
    )


def test_private_target_is_refused():
    with pytest.raises(TargetNotAllowedError):
        resolve_target("intranet.example.com", 443, resolver=resolver_returning("10.0.0.5"))


def test_cloud_metadata_target_is_refused():
    with pytest.raises(TargetNotAllowedError):
        resolve_target("evil.example.com", 443, resolver=resolver_returning("169.254.169.254"))


def test_one_private_address_among_public_ones_is_refused():
    resolver = resolver_returning("93.184.216.34", "127.0.0.1")
    with pytest.raises(TargetNotAllowedError):
        resolve_target("rebind.example.com", 443, resolver=resolver)


def test_ipv4_is_preferred_over_ipv6():
    resolver = resolver_returning("2606:4700:4700::1111", "1.1.1.1")
    assert resolve_target("example.com", 443, resolver=resolver) == "1.1.1.1"


def test_no_address_is_an_error():
    with pytest.raises(TargetUnresolvableError):
        resolve_target("example.com", 443, resolver=resolver_returning())


def test_private_targets_can_be_allowed_for_development():
    resolver = resolver_returning("127.0.0.1")
    assert resolve_target("dev.example.com", 443, allow_private=True, resolver=resolver) == (
        "127.0.0.1"
    )
