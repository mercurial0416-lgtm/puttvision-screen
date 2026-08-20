#!/usr/bin/env python3
"""Fetch the small CC0 asset set used by the V143 Godot TV renderer.

This is a build-time tool only. Selected Poly Haven asset pages mark their assets CC0.
The public API resolves PBR/HDR files, while high-resolution orthographic renders of the
same CC0 tree assets are used as lightweight horizon impostors instead of shipping a 95 MB
multi-million-triangle tree mesh in the Android APK.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import urllib.request

API = "https://api.polyhaven.com/files/{}"
USER_AGENT = "PuttVision-V143/1.0 (+https://github.com/mercurial0416-lgtm/puttvision-screen)"
ROOT = Path("app/src/main/assets/v143_assets")


def request_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=90) as response:
        return response.read()


def request_json(asset_id: str) -> dict:
    return json.loads(request_bytes(API.format(asset_id)).decode("utf-8"))


def write_file(url: str, path: Path, md5: str | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = request_bytes(url)
    if md5:
        actual = hashlib.md5(data).hexdigest()
        if actual.lower() != md5.lower():
            raise RuntimeError(f"MD5 mismatch for {url}: {actual} != {md5}")
    path.write_bytes(data)
    print(f"V143_ASSET {path} {len(data)} bytes")


def pick_resolution(tree: dict, preferred: tuple[str, ...]) -> str:
    for resolution in preferred:
        if resolution in tree:
            return resolution
    if not tree:
        raise KeyError("empty resolution tree")
    return sorted(tree.keys())[0]


def find_map(files: dict, names: tuple[str, ...]) -> dict:
    lowered = {str(k).lower().replace(" ", "_"): k for k in files.keys()}
    for name in names:
        key = lowered.get(name.lower().replace(" ", "_"))
        if key is not None:
            return files[key]
    for key, value in files.items():
        token = str(key).lower()
        if any(name.lower() in token for name in names):
            return value
    raise KeyError(f"map not found: {names}; have={list(files.keys())}")


def fetch_texture(asset_id: str, folder: str) -> None:
    files = request_json(asset_id)
    dest = ROOT / folder
    dest.mkdir(parents=True, exist_ok=True)
    wanted = {
        "albedo.png": ("diffuse", "diff"),
        "normal.png": ("nor_gl", "normal_gl", "normal"),
        "roughness.png": ("rough", "roughness"),
    }
    for out_name, aliases in wanted.items():
        tree = find_map(files, aliases)
        resolution = pick_resolution(tree, ("1k", "2k", "4k"))
        formats = tree[resolution]
        meta = formats.get("png") or formats.get("jpg") or next(iter(formats.values()))
        write_file(meta["url"], dest / out_name, meta.get("md5"))
    (dest / "source.json").write_text(
        json.dumps({"asset": asset_id, "resolution": "1k-preferred"}, indent=2),
        encoding="utf-8",
    )


def fetch_hdri(asset_id: str, folder: str) -> None:
    files = request_json(asset_id)
    hdri = files["hdri"]
    # 2K is still small enough for Android but avoids the blocky horizon visible with a 1K panorama.
    resolution = pick_resolution(hdri, ("2k", "1k", "4k"))
    formats = hdri[resolution]
    meta = formats.get("hdr") or formats.get("exr") or next(iter(formats.values()))
    dest = ROOT / folder
    dest.mkdir(parents=True, exist_ok=True)
    suffix = ".hdr" if "hdr" in formats else ".exr"
    write_file(meta["url"], dest / f"environment{suffix}", meta.get("md5"))
    (dest / "source.json").write_text(
        json.dumps({"asset": asset_id, "resolution": resolution}, indent=2),
        encoding="utf-8",
    )


def fetch_tree_impostor(asset_id: str, out_name: str) -> None:
    # Poly Haven's asset renders are transparent orthographic views of the CC0 model itself.
    # At the putting camera's horizon distance this is visually stronger and ~100x lighter than
    # carrying the original multi-million-triangle scan on a phone GPU.
    url = f"https://cdn.polyhaven.com/asset_img/renders/{asset_id}/orth_front.png?height=1536&quality=98"
    write_file(url, ROOT / "trees" / out_name)


def main() -> int:
    ROOT.mkdir(parents=True, exist_ok=True)
    fetch_texture("leafy_grass", "turf")
    fetch_tree_impostor("tree_small_02", "tree_small_02.png")
    fetch_tree_impostor("island_tree_03", "island_tree_03.png")
    fetch_hdri("limpopo_golf_course", "environment")

    license_text = """V143 third-party visual assets\n\nPoly Haven assets below are distributed under CC0 1.0.\n- Leafy Grass — https://polyhaven.com/a/leafy_grass\n- Tree Small 02 — https://polyhaven.com/a/tree_small_02\n- Island Tree 03 — https://polyhaven.com/a/island_tree_03\n- Limpopo Golf Course — https://polyhaven.com/a/limpopo_golf_course\n\nTree PNGs are orthographic renders of the listed CC0 models, used as mobile horizon impostors.\nAssets are fetched at build time; no Kakao VX/Friends Screen proprietary code, textures, meshes,\naudio, logos, characters, or private data are included.\n"""
    (ROOT / "ASSET_LICENSES.md").write_text(license_text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"V143 asset fetch failed: {exc}", file=sys.stderr)
        raise
