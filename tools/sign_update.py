#!/usr/bin/env python3
"""Offline update-manifest signer for NOX (Modul 19).

Usage:
  python3 tools/sign_update.py <jar> <download-url> [--min 0.1.0] [--key nox-bot/keys/license_private.pem]

Prints the signed manifest JSON to stdout (redirect to update.json).
The private key never leaves the release machine.
"""
import argparse
import base64
import hashlib
import json
import sys
import time
from pathlib import Path

from cryptography.hazmat.primitives import serialization


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("url")
    ap.add_argument("--min", dest="minver", default="0.1.0")
    ap.add_argument("--key", default="nox-bot/keys/license_private.pem")
    ap.add_argument("--version", default="")
    a = ap.parse_args()

    jar = Path(a.jar)
    sha = hashlib.sha256(jar.read_bytes()).hexdigest()
    # version: arg > filename pattern noxclient-X.Y.Z.jar > fabric.mod.json
    ver = a.version
    if not ver:
        stem = jar.stem
        ver = stem.split("-", 1)[1] if "-" in stem else ""
    if not ver:
        mod = Path("src/main/resources/fabric.mod.json")
        if mod.exists():
            ver = json.loads(mod.read_text())["version"]
    if not ver:
        sys.exit("could not determine version — pass --version")

    ts = int(time.time() * 1000)
    canon = f"{ver}|{a.url}|{sha}|{a.minver}|{ts}".encode()
    priv = serialization.load_pem_private_key(Path(a.key).read_bytes(), password=None)
    sig = priv.sign(canon)

    print(json.dumps({
        "version": ver,
        "url": a.url,
        "sha256": sha,
        "min_client_version": a.minver,
        "timestamp": ts,
        "sig": base64.urlsafe_b64encode(sig).decode(),
    }, indent=2))


if __name__ == "__main__":
    main()
