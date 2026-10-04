#!/usr/bin/env python3
"""Fails the build when a config rule names a geodata code that is not in the shipped .dat files."""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SOURCES = [
    os.path.join(ROOT, "app", "src", "main", "java"),
    os.path.join(ROOT, "app", "src", "engine", "java"),
    ASSETS,
]

RULE = re.compile(r'"(geosite|geoip|ext|ext-domain|ext-ip):([^"]+)"')


def read_varint(buf, i):
    shift = 0
    value = 0
    while True:
        byte = buf[i]
        i += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, i
        shift += 7


def entries(path):
    buf = open(path, "rb").read()
    i = 0
    total = len(buf)
    while i < total:
        key, i = read_varint(buf, i)
        if key & 7 != 2:
            raise ValueError("%s is not a plain list of length delimited entries" % path)
        length, i = read_varint(buf, i)
        body = buf[i:i + length]
        i += length
        j = 0
        code = None
        while j < len(body):
            field, j = read_varint(body, j)
            if field & 7 == 2:
                size, j = read_varint(body, j)
                if field >> 3 == 1 and code is None:
                    code = body[j:j + size].decode("utf-8", "replace")
                j += size
            elif field & 7 == 0:
                _, j = read_varint(body, j)
            elif field & 7 == 5:
                j += 4
            elif field & 7 == 1:
                j += 8
            else:
                raise ValueError("bad wire type %d in %s" % (field & 7, path))
        if not code:
            raise ValueError("%s holds an entry without a code" % path)
        yield code.upper()


def codes(path):
    return set(entries(path))


def references():
    domains, ips, ext = set(), set(), set()
    for base in SOURCES:
        for folder, _, files in os.walk(base):
            for name in files:
                if not name.endswith((".kt", ".json")) and not name.startswith("custom_routing"):
                    continue
                path = os.path.join(folder, name)
                if os.path.basename(path) == "GeoCatalog.kt":
                    continue
                with open(path, encoding="utf-8", errors="replace") as handle:
                    text = handle.read()
                for kind, body in RULE.findall(text):
                    code = body.split(":")[-1].strip().upper()
                    if kind == "geosite":
                        domains.add(code)
                    elif kind == "geoip":
                        ips.add(code)
                    else:
                        ext.add((body.split(":")[0], code))
    return domains, ips, ext


def check(name, used, shipped, what):
    missing = sorted(used - shipped)
    if missing:
        print("FAIL: %s reference codes that %s does not hold: %s" % (what, name, missing))
        return 1
    print("ok: %s knows every %s code (%d)" % (name, what, len(used)))
    return 0


def main():
    geosite = codes(os.path.join(ASSETS, "geosite.dat"))
    geoip = codes(os.path.join(ASSETS, "geoip.dat"))
    cn_private = codes(os.path.join(ASSETS, "geoip-only-cn-private.dat"))

    domains, ips, ext = references()
    failed = check("geosite.dat", domains, geosite, "geosite")
    failed += check("geoip.dat", ips, geoip, "geoip")
    for file_name, code in sorted(ext):
        table = cn_private if "cn-private" in file_name else (geosite if "geosite" in file_name else geoip)
        if code not in table:
            print("FAIL: ext reference %s:%s is not in the shipped %s" % (file_name, code, file_name))
            failed += 1
    if not failed:
        print("ok: every ext reference resolves against the bundled files")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
