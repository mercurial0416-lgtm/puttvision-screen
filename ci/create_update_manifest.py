#!/usr/bin/env python3
"""Create the public update manifest without shell-string interpolation."""

import argparse
import json
from pathlib import Path
from urllib.parse import urlparse


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    if args.version_code <= 0:
        parser.error("version-code must be positive")
    if urlparse(args.apk_url).scheme != "https":
        parser.error("apk-url must use HTTPS")
    digest = args.sha256.lower()
    if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
        parser.error("sha256 must be 64 hexadecimal characters")

    manifest = {
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "apkUrl": args.apk_url,
        "sha256": digest,
    }
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
