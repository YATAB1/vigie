"""Hostname validation shared by every job that touches the network."""

import re

_LABEL = r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
# At least two labels and an alphabetic TLD: rejects IP literals and bare names.
_HOSTNAME = re.compile(rf"^(?:{_LABEL}\.)+(?:[a-z]{{2,63}}|xn--[a-z0-9-]{{1,59}})$")
# DNS record names may also contain underscore-prefixed labels (_dmarc, _vigie-challenge).
_RECORD_NAME = re.compile(rf"^(?:_?{_LABEL}\.)+(?:[a-z]{{2,63}}|xn--[a-z0-9-]{{1,59}})$")

MAX_LENGTH = 253


def is_valid_hostname(value: str) -> bool:
    return len(value) <= MAX_LENGTH and _HOSTNAME.fullmatch(value) is not None


def is_valid_record_name(value: str) -> bool:
    return len(value) <= MAX_LENGTH and _RECORD_NAME.fullmatch(value) is not None
