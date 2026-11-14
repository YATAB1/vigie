"""Network access for scans, with SSRF protection.

A scan connects to a hostname chosen by a user. Without care, that user could
point the scanner at an internal service (a database, a cloud metadata endpoint)
and read the answer through the scan report. Two rules prevent this:

1. The hostname is resolved once and every address must be public.
2. The connection is made to that vetted IP address, not to the hostname, so a
   second DNS answer cannot redirect it elsewhere (DNS rebinding).
"""

import ipaddress
import socket
import ssl
from collections.abc import Callable

Resolver = Callable[[str, int], list[str]]


class TargetNotAllowedError(Exception):
    """The hostname resolves to an address the scanner must never contact."""


class TargetUnresolvableError(Exception):
    """The hostname has no usable address."""


def is_public_address(address: str) -> bool:
    ip = ipaddress.ip_address(address)
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
        ip = ip.ipv4_mapped
    # is_global is False for private, loopback, link-local (cloud metadata),
    # carrier-grade NAT, documentation and unspecified ranges.
    return ip.is_global and not ip.is_multicast


def system_resolver(hostname: str, port: int) -> list[str]:
    try:
        infos = socket.getaddrinfo(hostname, port, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise TargetUnresolvableError(f"résolution DNS impossible pour {hostname}") from exc
    return [info[4][0] for info in infos]


def resolve_target(
    hostname: str,
    port: int,
    *,
    allow_private: bool = False,
    resolver: Resolver = system_resolver,
) -> str:
    """Returns the single IP address a scan is allowed to connect to."""
    addresses = list(dict.fromkeys(resolver(hostname, port)))
    if not addresses:
        raise TargetUnresolvableError(f"aucune adresse pour {hostname}")
    if not allow_private:
        forbidden = [a for a in addresses if not is_public_address(a)]
        if forbidden:
            # One internal address among public ones is treated as hostile.
            raise TargetNotAllowedError(
                f"{hostname} pointe vers une adresse non publique et ne peut pas être analysé"
            )
    # Prefer IPv4: many container networks have no IPv6 route.
    addresses.sort(key=lambda a: ipaddress.ip_address(a).version)
    return addresses[0]


def open_tcp(ip: str, port: int, timeout: float) -> socket.socket:
    return socket.create_connection((ip, port), timeout=timeout)


def open_tls(
    hostname: str, ip: str, port: int, context: ssl.SSLContext, timeout: float
) -> ssl.SSLSocket:
    """Connects to the vetted IP while presenting ``hostname`` for SNI and validation."""
    sock = open_tcp(ip, port, timeout)
    try:
        return context.wrap_socket(sock, server_hostname=hostname)
    except BaseException:
        sock.close()
        raise
