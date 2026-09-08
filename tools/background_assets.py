#!/usr/bin/env python3
"""Rebuild, verify and visually compare NORA's offline Android backgrounds."""

import argparse
import hashlib
import io
import json
import math
from pathlib import Path

import PIL
from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageOps, ImageStat, features

ROOT = Path(__file__).resolve().parents[1]
MASTERS = ROOT / "assets/backgrounds/masters"
SHIPPING = ROOT / "app/src/main/res/drawable-nodpi"
MANIFEST = ROOT / "assets/backgrounds/manifest.json"
EXPECTED_PILLOW = "11.3.0"
EXPECTED_WEBP = "1.5.0"
QUALITY = 95
CANDIDATES = (88, 92, 95)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def sources():
    files = sorted(MASTERS.glob("nora_location_*.png"))
    if not files:
        raise ValueError(f"No source masters in {MASTERS}")
    return files


def encoder():
    actual = {"pillow": PIL.__version__, "libwebp": features.version("webp")}
    if actual != {"pillow": EXPECTED_PILLOW, "libwebp": EXPECTED_WEBP}:
        raise ValueError(f"Use Pillow {EXPECTED_PILLOW} / libwebp {EXPECTED_WEBP}; found {actual}")
    return actual


def encode(source, quality=QUALITY):
    with Image.open(source) as image:
        image.load()
        # Preserve all RGBA pixels, including hidden RGB at transparent edges.
        has_alpha = "A" in image.getbands() or "transparency" in image.info
        image = image.convert("RGBA" if has_alpha else "RGB")
        parameters = {"lossless": has_alpha, "quality": 100 if has_alpha else quality,
                      "method": 6, "exact": True}
        stream = io.BytesIO()
        image.save(stream, format="WEBP", **parameters)
        data = stream.getvalue()
        with Image.open(io.BytesIO(data)) as decoded:
            decoded.load()
            if decoded.size != image.size:
                raise ValueError(f"Dimensions changed: {source.name}")
            if has_alpha and decoded.convert("RGBA").tobytes() != image.tobytes():
                raise ValueError(f"RGBA pixels changed: {source.name}")
        return data, image.size, image.mode, parameters


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def build():
    version = encoder()
    entries = []
    for source in sources():
        if (SHIPPING / source.name).exists():
            raise ValueError(f"Duplicate Android resource: {source.name}; keep PNG masters outside app/")
        data, size, mode, parameters = encode(source)
        target = SHIPPING / (source.stem + ".webp")
        target.write_bytes(data)
        original = source.read_bytes()
        entries.append({"resource": source.stem, "source": source.relative_to(ROOT).as_posix(),
                        "source_sha256": sha256(original), "source_bytes": len(original),
                        "output": target.relative_to(ROOT).as_posix(), "output_sha256": sha256(data),
                        "output_bytes": len(data), "width": size[0], "height": size[1],
                        "mode": mode, "parameters": parameters})
        print(f"{source.stem}: {len(original):,} -> {len(data):,}", flush=True)
    before = sum(entry["source_bytes"] for entry in entries)
    after = sum(entry["output_bytes"] for entry in entries)
    write_json(MANIFEST, {"schema_version": 1, "encoder": version,
                         "policy": "native dimensions; opaque WebP q95; RGBA lossless exact",
                         "asset_count": len(entries), "source_bytes": before, "shipping_bytes": after,
                         "reduction_percent": round(100 * (1 - after / before), 4), "assets": entries})
    verify()


def verify(reencode=False):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    expected_sources = {entry["source"] for entry in manifest["assets"]}
    if expected_sources != {path.relative_to(ROOT).as_posix() for path in sources()}:
        raise ValueError("Master inventory changed; rebuild the asset manifest")
    expected = {entry["output"] for entry in manifest["assets"]}
    actual = {path.relative_to(ROOT).as_posix() for path in SHIPPING.glob("nora_location_*")}
    if expected != actual:
        raise ValueError(f"Shipping inventory differs: {expected.symmetric_difference(actual)}")
    if reencode:
        encoder()
    for entry in manifest["assets"]:
        source, target = ROOT / entry["source"], ROOT / entry["output"]
        for path, field in ((source, "source"), (target, "output")):
            data = path.read_bytes()
            if sha256(data) != entry[field + "_sha256"] or len(data) != entry[field + "_bytes"]:
                raise ValueError(f"Hash/size mismatch: {path}")
        with Image.open(target) as image:
            image.load()
            if image.size != (entry["width"], entry["height"]):
                raise ValueError(f"Wrong decoded dimensions: {target.name}")
            if entry["mode"] == "RGBA":
                with Image.open(source) as original:
                    if image.convert("RGBA").tobytes() != original.convert("RGBA").tobytes():
                        raise ValueError(f"RGBA pixels changed: {target.name}")
        if reencode and encode(source)[0] != target.read_bytes():
            raise ValueError(f"Rebuild is not byte-identical: {target.name}")
    print(f"Verified {len(expected)} offline assets; {manifest['source_bytes']:,} -> "
          f"{manifest['shipping_bytes']:,} bytes ({manifest['reduction_percent']:.2f}% smaller).")


def rgb_on_checker(image):
    if image.mode != "RGBA":
        return image.convert("RGB")
    checker = Image.new("RGBA", image.size, "#555555")
    draw = ImageDraw.Draw(checker)
    for y in range(0, image.height, 24):
        for x in range(0, image.width, 24):
            if (x // 24 + y // 24) % 2:
                draw.rectangle((x, y, x + 23, y + 23), fill="#aaaaaa")
    return Image.alpha_composite(checker, image).convert("RGB")


def font(size=18):
    for name in ("C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default(size=size)


def gallery(output):
    files = sources()
    width, height = 340, 225
    sheet = Image.new("RGB", (width * 4, height * math.ceil(len(files) / 4)), "#171b24")
    draw = ImageDraw.Draw(sheet)
    for index, source in enumerate(files):
        with Image.open(source) as original:
            preview = rgb_on_checker(original)
            preview.thumbnail((width - 12, height - 40), Image.Resampling.LANCZOS)
        x, y = index % 4 * width, index // 4 * height
        sheet.paste(preview, (x + (width - preview.width) // 2, y))
        draw.text((x + 6, y + height - 31), source.stem.removeprefix("nora_location_"), font=font(), fill="white")
    output.mkdir(parents=True, exist_ok=True)
    sheet.save(output / "masters-gallery.jpg", quality=95)
    print(output / "masters-gallery.jpg")


def psnr(original, decoded):
    difference = ImageChops.difference(original.convert("RGB"), decoded.convert("RGB"))
    mse = sum(value * value for value in ImageStat.Stat(difference).rms) / 3
    return None if mse == 0 else round(10 * math.log10(255 * 255 / mse), 3)


def compare(output, names, display_size):
    encoder()
    output.mkdir(parents=True, exist_ok=True)
    selected = sources() if not names else [MASTERS / ("nora_location_" + name + ".png") for name in names]
    results = []
    for source in selected:
        with Image.open(source) as opened:
            original = opened.convert("RGBA" if "A" in opened.getbands() else "RGB")
        versions = [("PNG master", original)]
        row = {"resource": source.stem, "source_bytes": source.stat().st_size, "candidates": []}
        for quality in CANDIDATES:
            data, _, _, parameters = encode(source, quality)
            target = output / (source.stem + f"-q{quality}.webp")
            target.write_bytes(data)
            with Image.open(io.BytesIO(data)) as image:
                decoded = image.copy()
            label = "lossless" if parameters["lossless"] else f"q{quality}"
            versions.append((f"{label}: {len(data) / 1024:.0f} KiB", decoded))
            row["candidates"].append({"quality": label, "bytes": len(data), "rgb_psnr_db": psnr(original, decoded)})
        pair = Image.new("RGB", (display_size[0], (display_size[1] + 36) * 2), "#171b24")
        pair_draw = ImageDraw.Draw(pair)
        for index, (_, picture) in enumerate(versions):
            # Match centered ContentScale.Crop at the measured physical pixel bounds.
            display = ImageOps.fit(rgb_on_checker(picture), display_size, method=Image.Resampling.LANCZOS)
            suffix = "master" if index == 0 else f"q{CANDIDATES[index - 1]}"
            display.save(output / (source.stem + f"-display-{suffix}.png"))
            if index in (0, len(versions) - 1):
                top = 0 if index == 0 else display_size[1] + 36
                pair.paste(display, (0, top + 36))
                pair_draw.text((8, top + 8), f"{suffix} / {display_size[0]}x{display_size[1]} physical px", font=font(), fill="white")
        pair.save(output / (source.stem + "-display-pair.png"))
        # Every crop retains 1 source pixel per output pixel; no resampling hides artifacts.
        tile, title = 512, 42
        sheet = Image.new("RGB", (tile * 4, (tile + title) * 3), "#171b24")
        draw = ImageDraw.Draw(sheet)
        positions = (("upper", (original.width - tile) // 2, 0),
                     ("center", (original.width - tile) // 2, (original.height - tile) // 2),
                     ("lower", (original.width - tile) // 2, original.height - tile))
        for column, (label, picture) in enumerate(versions):
            picture = rgb_on_checker(picture)
            for line, (position, x, y) in enumerate(positions):
                top = line * (tile + title)
                sheet.paste(picture.crop((x, y, x + tile, y + tile)), (column * tile, top + title))
                draw.text((column * tile + 8, top + 10), f"{label} / {position} 1:1", font=font(), fill="white")
        sheet.save(output / (source.stem + "-comparison.png"))
        results.append(row)
        print(source.stem, row["candidates"], flush=True)
    write_json(output / "comparison.json", {"encoder": encoder(), "display_size_px": list(display_size), "metrics_note": "RGB PSNR is diagnostic only; visually inspect native 1:1 crops and device rendering.", "assets": results})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("build", "verify", "gallery", "compare"))
    parser.add_argument("--reencode", action="store_true", help="verify a byte-identical rebuild")
    parser.add_argument("--output", type=Path, default=ROOT / "build/background-qa")
    parser.add_argument("--names", nargs="*", help="region stems, e.g. antarctica1 finland1 universe")
    parser.add_argument("--display-size", type=int, nargs=2, default=(990, 534), metavar=("WIDTH", "HEIGHT"),
                        help="physical pixel bounds for centered display crops (default: measured phone card 990x534)")
    args = parser.parse_args()
    if args.command == "build":
        build()
    elif args.command == "verify":
        verify(args.reencode)
    elif args.command == "gallery":
        gallery(args.output)
    elif args.command == "compare":
        if min(args.display_size) <= 0:
            parser.error("display dimensions must be positive")
        compare(args.output, args.names, tuple(args.display_size))


if __name__ == "__main__":
    main()
