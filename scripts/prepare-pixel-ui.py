#!/usr/bin/env python3
"""Reproduce the supplied pixel UI artwork without interpolation or redrawing.

Run without arguments to rebuild from branding/pixel-ui/originals. On the first
run, --source-map accepts a JSON object mapping attachment numbers 1..12 to PNG
paths. Original files are copied byte-for-byte and never replaced with different
contents. Pillow is the only dependency.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

from PIL import Image, ImageDraw, ImageFont, __version__ as PILLOW_VERSION


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "branding" / "pixel-ui"
ORIGINALS = ASSETS / "originals"
ARCHIVE = ROOT / "dist" / "typinganimation-pixel-ui.zip"
CONTACT_SHEET = ROOT / ".research" / "pixel-ui-contact-sheet.png"

SPECS = [
    {"number": 1, "name": "divider-01", "size": (96, 12), "scales": (3, 6, 8)},
    {"number": 2, "name": "divider-02", "size": (96, 12), "scales": (3, 6, 8), "black_background": 796},
    {"number": 3, "name": "divider-03", "size": (96, 12), "scales": (3, 6, 8), "black_background": 700},
    {"number": 4, "name": "divider-04", "size": (96, 12), "scales": (3, 6, 8)},
    {"number": 5, "name": "platform-modrinth", "size": (16, 16), "scales": (4,)},
    {"number": 6, "name": "platform-curseforge", "size": (16, 16), "scales": (4,)},
    {"number": 7, "name": "platform-github", "size": (16, 16), "scales": (4,)},
    {"number": 8, "name": "mini-icon-01", "size": (12, 12), "scales": (2, 4)},
    {"number": 9, "name": "mini-icon-02", "size": (12, 12), "scales": (2, 4)},
    {"number": 10, "name": "mini-icon-03", "size": (12, 12), "scales": (2, 4)},
    {"number": 11, "name": "mini-icon-04", "size": (12, 12), "scales": (2, 4)},
    {"number": 12, "name": "point", "size": (16, 16), "scales": (2,)},
]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pixels(image: Image.Image) -> list[tuple[int, int, int, int]]:
    return list(image.get_flattened_data())


def alpha_counts(image: Image.Image) -> dict[str, int]:
    return {str(alpha): count for alpha, count in sorted(Counter(image.getchannel("A").tobytes()).items())}


def save_png(image: Image.Image, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    # No inherited metadata, palette conversion, antialiasing, or color correction.
    image.save(destination, format="PNG", optimize=False, compress_level=9)


def verify_nearest(source: Image.Image, result: Image.Image, scale: int) -> dict:
    assert result.mode == "RGBA", "Web assets must preserve an explicit alpha channel"
    assert result.size == (source.width * scale, source.height * scale)
    source_pixels = source.load()
    result_pixels = result.load()
    for y in range(source.height):
        for x in range(source.width):
            expected = source_pixels[x, y]
            for dy in range(scale):
                for dx in range(scale):
                    assert result_pixels[x * scale + dx, y * scale + dy] == expected, (x, y, dx, dy)
    assert set(pixels(source)) == set(pixels(result)), "Scaling introduced or removed a color"
    expected_alpha = {alpha: count * scale * scale for alpha, count in alpha_counts(source).items()}
    assert alpha_counts(result) == expected_alpha, "Scaling changed an alpha value"
    return {"nearest_blocks_exact": True, "rgba_palette_exact": True, "alpha_counts": expected_alpha}


def import_originals(source_map: dict[str, str]) -> dict[str, str]:
    if source_map and set(source_map) != {str(spec["number"]) for spec in SPECS}:
        raise ValueError("--source-map must contain exactly the keys 1 through 12")
    source_names = {}
    prior_manifest = ASSETS / "manifest.json"
    if prior_manifest.exists():
        source_names = {
            str(item["attachment"]): item["source_filename"]
            for item in json.loads(prior_manifest.read_text(encoding="utf-8"))["sources"]
        }
    ORIGINALS.mkdir(parents=True, exist_ok=True)
    for spec in SPECS:
        key = str(spec["number"])
        destination = ORIGINALS / (spec["name"] + ".png")
        if source_map:
            source = Path(source_map[key])
            original_bytes = source.read_bytes()
            if destination.exists() and destination.read_bytes() != original_bytes:
                raise ValueError(f"Refusing to replace a different original: {destination}")
            if not destination.exists():
                shutil.copyfile(source, destination)
            assert destination.read_bytes() == original_bytes
            source_names[key] = source.name
        elif not destination.is_file():
            raise FileNotFoundError(f"Missing {destination}; import the 12 attachments with --source-map first")
        source_names.setdefault(key, destination.name)
    return source_names


def prepare(source_names: dict[str, str]) -> tuple[dict, list[Path]]:
    sources, outputs, generated = [], [], []
    for spec in SPECS:
        original = ORIGINALS / (spec["name"] + ".png")
        with Image.open(original) as source_image:
            source_image.load()
            assert source_image.size == spec["size"], f"Unexpected source dimensions: {original}"
            source_mode = source_image.mode
            rgba = source_image.convert("RGBA")
        source_pixels = pixels(rgba)
        cleaned = rgba.copy()
        cleanup_count = spec.get("black_background", 0)
        if cleanup_count:
            assert source_mode == "RGB", "Only the two explicitly identified RGB backgrounds are cleaned"
            assert sum(pixel[:3] == (0, 0, 0) for pixel in source_pixels) == cleanup_count
            cleaned.putdata([(red, green, blue, 0 if (red, green, blue) == (0, 0, 0) else alpha)
                             for red, green, blue, alpha in source_pixels])
            for before, after in zip(source_pixels, pixels(cleaned), strict=True):
                assert before[:3] == after[:3], "Background cleanup must not alter any RGB value"
                expected = (0, 0, 0, 0) if before[:3] == (0, 0, 0) else before
                assert after == expected
        else:
            assert pixels(cleaned) == source_pixels, "Unrequested transparency change"
        if 8 <= spec["number"] <= 11:
            assert alpha_counts(cleaned) == {"255": 144}, "Purple mini-icon corners must remain opaque"
        source_record = {
            "attachment": spec["number"], "canonical_name": spec["name"],
            "source_filename": source_names[str(spec["number"])],
            "original": original.relative_to(ASSETS).as_posix(), "sha256": sha256(original.read_bytes()),
            "original_mode": source_mode, "dimensions": list(rgba.size),
            "original_alpha_counts": alpha_counts(rgba), "web_alpha_counts": alpha_counts(cleaned),
            "cleanup": "pure_black_background_to_transparent" if cleanup_count else "none",
            "cleanup_pixels": cleanup_count,
            "original_bytes_preserved": True,
        }
        sources.append(source_record)
        variants = [(spec["name"], cleaned, scale, None) for scale in spec["scales"]]
        if spec["number"] == 12:
            bounds = cleaned.getchannel("A").getbbox()
            assert bounds == (4, 4, 12, 12), "Inline point must be the exact supplied 8-by-8 alpha bounds"
            variants.append(("point-inline", cleaned.crop(bounds), 2, list(bounds)))
        for name, base, scale, crop in variants:
            result = base.resize((base.width * scale, base.height * scale), Image.Resampling.NEAREST)
            destination = ASSETS / f"{name}-{scale}x.png"
            save_png(result, destination)
            with Image.open(destination) as saved:
                saved.load()
                verification = verify_nearest(base, saved, scale)
            outputs.append({
                "file": destination.name, "attachment": spec["number"], "scale": scale,
                "source_crop": crop, "dimensions": list(result.size), "mode": "RGBA",
                "sha256": sha256(destination.read_bytes()), "bytes": destination.stat().st_size,
                "verification": verification,
            })
            generated.append(destination)
    manifest = {
        "schema_version": 1, "resampler": "Pillow.Image.Resampling.NEAREST", "pillow_version": PILLOW_VERSION,
        "source_count": len(sources), "usable_png_count": len(outputs),
        "archive": "dist/typinganimation-pixel-ui.zip", "sources": sources, "outputs": outputs,
    }
    return manifest, generated


def write_readme(manifest: dict) -> Path:
    rows = []
    for source in manifest["sources"]:
        outputs = [item for item in manifest["outputs"] if item["attachment"] == source["attachment"]]
        variants = ", ".join(f'`{item["file"]}` ({item["dimensions"][0]}×{item["dimensions"][1]})' for item in outputs)
        cleanup = f'Only {source["cleanup_pixels"]} pure-black background pixels made transparent' if source["cleanup_pixels"] else "Unchanged RGBA pixels"
        rows.append(f'| {source["attachment"]} | `{source["original"]}` | {variants} | {cleanup} |')
    text = """# Pixel UI artwork

Twelve user-supplied pixel PNGs, preserved as original files and exported as 25 crisp, usable PNG variants. Every enlargement uses an integer scale and nearest-neighbor sampling. No artwork was redrawn and no smoothing, blur, or new colors were added.

## Files and mapping

| Attachment | Byte-for-byte original | Usable variants | Transparency handling |
| --- | --- | --- | --- |
""" + "\n".join(rows) + """

The mini icons from attachments 8–11 intentionally have opaque purple corners. Those pixels remain opaque in every output. The inline point is the original point's exact alpha bounding box `(4, 4, 12, 12)`, cropped to 8×8 before a 2× enlargement to 16×16. The uncropped point is also provided at 32×32.

Only attachments 2 and 3 need background cleanup for web use: their original RGB files have 796 and 700 pure-black `(0, 0, 0)` background pixels respectively. Those pixels receive alpha 0 in the derived PNGs. Every RGB triplet is retained, every nonblack pixel is unchanged, and both originals remain byte-for-byte intact.

Use the 288×36 divider variants in compact/mobile descriptions; 576×72 and 768×96 variants are also available. Render a PNG at its natural dimensions, or another integer scale, to keep its pixel grid sharp.

## Reproduce and verify

Run `python scripts/prepare-pixel-ui.py` from the repository. It rebuilds from the preserved originals, verifies every enlarged pixel block, RGBA palette and alpha histogram, writes `manifest.json`, and creates `dist/typinganimation-pixel-ui.zip`. Initial import uses `--source-map path/to/map.json`, where the JSON object maps attachment numbers `"1"` through `"12"` to the source PNG paths. Import refuses to replace an original with different bytes.

The manifest records the attachment mapping, source SHA-256 hashes, scale factors, dimensions, alpha cleanup and output hashes. PNGs omit inherited metadata and use fixed compression settings. ZIP entries use sorted names, fixed timestamps and fixed permissions, so repeated builds with the same Pillow runtime are byte-identical. The ZIP contains all 25 usable PNGs, the 12 originals, this README and the manifest.

The script also writes `.research/pixel-ui-contact-sheet.png` for visual review on light and dark backgrounds. This review sheet is excluded from the distributable archive.
"""
    destination = ASSETS / "README.md"
    destination.write_text(text, encoding="utf-8", newline="\n")
    return destination


def write_archive(files: list[Path]) -> None:
    ARCHIVE.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ARCHIVE, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(files, key=lambda item: item.relative_to(ASSETS).as_posix()):
            info = zipfile.ZipInfo(path.relative_to(ASSETS).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(ARCHIVE) as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == len(files)
        for path in files:
            assert archive.read(path.relative_to(ASSETS).as_posix()) == path.read_bytes()


def write_contact_sheet() -> None:
    sheet = Image.new("RGB", (1664, 920), "#ddd8e8")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=18)
    for x, color, foreground in [(16, "#f5f1ff", "#221b31"), (840, "#17141f", "#f1eaff")]:
        draw.rectangle((x, 12, x + 807, 908), fill=color)
        draw.text((x + 20, 25), "Light background" if x == 16 else "Dark background", fill=foreground, font=font)
        for index in range(1, 5):
            y = 60 + (index - 1) * 145
            draw.text((x + 20, y), f"#{index} divider-{index:02d}-8x.png / 768 x 96", fill=foreground, font=font)
            with Image.open(ASSETS / f"divider-{index:02d}-8x.png") as asset:
                sheet.paste(asset, (x + 20, y + 30), asset)
        draw.text((x + 20, 650), "#5 Modrinth   #6 CurseForge   #7 GitHub / 64 x 64", fill=foreground, font=font)
        for index, name in enumerate(("modrinth", "curseforge", "github")):
            with Image.open(ASSETS / f"platform-{name}-4x.png") as asset:
                sheet.paste(asset, (x + 20 + index * 150, 680), asset)
        draw.text((x + 20, 760), "#8-11 mini icons / 48 x 48 (purple corners retained)", fill=foreground, font=font)
        for index in range(1, 5):
            with Image.open(ASSETS / f"mini-icon-{index:02d}-4x.png") as asset:
                sheet.paste(asset, (x + 20 + (index - 1) * 120, 790), asset)
        draw.text((x + 20, 860), "#12 point / 32 x 32; inline / 16 x 16", fill=foreground, font=font)
        for offset, name in [(470, "point-2x.png"), (530, "point-inline-2x.png")]:
            with Image.open(ASSETS / name) as asset:
                sheet.paste(asset, (x + offset, 850), asset)
    save_png(sheet, CONTACT_SHEET)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-map", type=Path)
    args = parser.parse_args()
    source_map = json.loads(args.source_map.read_text(encoding="utf-8-sig")) if args.source_map else {}
    source_names = import_originals(source_map)
    manifest, generated = prepare(source_names)
    manifest_file = ASSETS / "manifest.json"
    manifest_file.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    readme = write_readme(manifest)
    originals = [ORIGINALS / (spec["name"] + ".png") for spec in SPECS]
    write_archive(generated + originals + [manifest_file, readme])
    write_contact_sheet()
    print(json.dumps({"sources": len(originals), "usable_pngs": len(generated),
                      "zip_entries": len(generated) + len(originals) + 2,
                      "archive": ARCHIVE.relative_to(ROOT).as_posix(), "sha256": sha256(ARCHIVE.read_bytes()),
                      "verification": "all nearest-neighbor blocks, RGBA palettes, alpha histograms and archive entries match"}, indent=2))


if __name__ == "__main__":
    main()
