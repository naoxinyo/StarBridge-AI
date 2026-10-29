#!/usr/bin/env python3
"""Restore unchanged runtime assets from the published Dev 1 APK."""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import zipfile

EXPECTED_SHA256 = "ed998a3f38ff20ee5751725deead480c1ed873536982997b6bf18fdacc3d137e"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    digest = hashlib.sha256()
    with args.apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != EXPECTED_SHA256:
        raise SystemExit("APK SHA-256 does not match the published StarBridge Dev 1 build")
    source_root = Path(__file__).resolve().parents[1]
    output = (source_root.parent / "runtime" / "android-assets").resolve()
    count = 0
    with zipfile.ZipFile(args.apk) as archive:
        entries = [i for i in archive.infolist() if not i.is_dir()
                   and i.filename.startswith(("assets/runtime/", "assets/dsh/"))]
        if not entries:
            raise SystemExit("Runtime assets not found")
        planned = []
        for entry in entries:
            relative = PurePosixPath(entry.filename).relative_to("assets")
            if ".." in relative.parts or "\\" in entry.filename:
                raise SystemExit("Invalid asset path")
            target = output.joinpath(*relative.parts).resolve()
            if output not in target.parents:
                raise SystemExit("Asset path escaped destination")
            data = archive.read(entry)
            if target.exists() and target.read_bytes() != data:
                raise SystemExit("Existing runtime asset has different content; nothing overwritten")
            planned.append((target, data))
        for target, data in planned:
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists():
                target.write_bytes(data)
            count += 1
    print(f"Restored or verified {count} unchanged runtime assets")


if __name__ == "__main__":
    main()
