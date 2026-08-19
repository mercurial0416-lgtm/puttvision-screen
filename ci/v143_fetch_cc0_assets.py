#!/usr/bin/env python3
"""Fetch the small CC0 asset set used by the V143 Godot TV renderer.

This is a build-time tool only. Assets are downloaded from Poly Haven, whose asset pages
mark the selected assets CC0. The public API is used to resolve the exact 1K files and model
includes so the project does not carry huge source binaries in git while V143 is being tuned.
"""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
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


def pick_resolution(tree: dict, preferred: tuple[str, ...] = ("1k", "2k", "4k")) -> str:
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
        resolution = pick_resolution(tree)
        formats = tree[resolution]
        meta = formats.get("png") or formats.get("jpg") or next(iter(formats.values()))
        write_file(meta["url"], dest / out_name, meta.get("md5"))
    (dest / "source.json").write_text(json.dumps({"asset": asset_id, "resolution": "1k-preferred"}, indent=2), encoding="utf-8")


def fetch_hdri(asset_id: str, folder: str) -> None:
    files = request_json(asset_id)
    hdri = files["hdri"]
    resolution = pick_resolution(hdri)
    formats = hdri[resolution]
    meta = formats.get("hdr") or formats.get("exr") or next(iter(formats.values()))
    dest = ROOT / folder
    dest.mkdir(parents=True, exist_ok=True)
    suffix = ".hdr" if "hdr" in formats else ".exr"
    write_file(meta["url"], dest / f"environment{suffix}", meta.get("md5"))
    (dest / "source.json").write_text(json.dumps({"asset": asset_id, "resolution": resolution}, indent=2), encoding="utf-8")


def fetch_gltf(asset_id: str, folder: str) -> None:
    files = request_json(asset_id)
    gltf_tree = files["gltf"]
    resolution = pick_resolution(gltf_tree)
    entry = gltf_tree[resolution]["gltf"]
    dest = ROOT / folder
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)
    write_file(entry["url"], dest / "model.gltf", entry.get("md5"))
    includes = entry.get("include") or {}
    for rel_path, meta in includes.items():
        safe = Path(rel_path)
        if safe.is_absolute() or ".." in safe.parts:
            raise RuntimeError(f"unsafe include path: {rel_path}")
        write_file(meta["url"], dest / safe, meta.get("md5"))
    (dest / "source.json").write_text(json.dumps({"asset": asset_id, "resolution": resolution, "includes": len(includes)}, indent=2), encoding="utf-8")


def main() -> int:
    ROOT.mkdir(parents=True, exist_ok=True)
    # Muted natural ground scan; V143 retints it for putting green/fringe/rough layers.
    fetch_texture("leafy_grass", "turf")
    # Low-resolution broadleaf model with real branches/leaves and LOD source data.
    fetch_gltf("tree_small_02", "tree_small_02")
    # Bright golf-course environment for realistic sky reflections / ambient color.
    fetch_hdri("limpopo_golf_course", "environment")
    license_text = """V143 third-party visual assets\n\nPoly Haven assets below are distributed under CC0 1.0.\n- Leafy Grass — https://polyhaven.com/a/leafy_grass\n- Tree Small 02 — https://polyhaven.com/a/tree_small_02\n- Limpopo Golf Course — https://polyhaven.com/a/limpopo_golf_course\n\nDownloaded at build time via the Poly Haven public API. PuttVision does not ship or use\nKakao VX/Friends Screen proprietary code, textures, meshes, audio, logos, or private data.\n"""
    (ROOT / "ASSET_LICENSES.md").write_text(license_text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"V143 asset fetch failed: {exc}", file=sys.stderr)
        raise
