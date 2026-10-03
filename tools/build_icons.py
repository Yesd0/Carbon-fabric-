#!/usr/bin/env python3
"""Rasterize the approved Lucide SVG icons and build Carbon's GUI atlas.

Build-time only; the mod itself has no Python or SVG-renderer dependency.
Install with: python -m pip install -r tools/requirements-icons.txt
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw
from svgpathtools import parse_path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "tools/icons-src"
ATLAS_PATH = ROOT / "src/main/resources/assets/carbonclient/textures/gui/icons.png"
INDEX_PATH = ROOT / "src/main/resources/assets/carbonclient/textures/gui/icons.json"
LICENSE_PATH = ROOT / "src/main/resources/META-INF/licenses/Lucide-ISC-and-Feather-MIT.txt"
PREVIEW_DIR = ROOT / "build/carbon-icons"
ICON_NAMES = (
    "layout-grid",
    "layout-dashboard",
    "user",
    "settings",
    "search",
    "x",
    "keyboard",
    "mouse-pointer-click",
    "gauge",
    "zoom-in",
    "shield",
    "flask-conical",
    "eye",
    "map-pin",
    "crosshair",
    "blend",
    "sliders-horizontal",
)
ICON_SIZE = 96
CELL_SIZE = 112
PADDING = (CELL_SIZE - ICON_SIZE) // 2
SUPERSAMPLE = 4
COLUMNS = 6
WHITE = (255, 255, 255, 255)


def local_tag(element: ET.Element) -> str:
    return element.tag.rsplit("}", 1)[-1]


def number(element: ET.Element, key: str, default: float = 0.0) -> float:
    value = element.get(key)
    return default if value is None else float(value)


def render_icon(svg_path: Path, output_path: Path) -> Image.Image:
    root = ET.parse(svg_path).getroot()
    view_box = [float(value) for value in root.get("viewBox", "0 0 24 24").replace(",", " ").split()]
    if len(view_box) != 4:
        raise ValueError(f"Invalid SVG viewBox in {svg_path}")
    view_x, view_y, view_width, view_height = view_box
    if view_width <= 0 or view_height <= 0:
        raise ValueError(f"Empty SVG viewBox in {svg_path}")

    high_size = ICON_SIZE * SUPERSAMPLE
    scale_x = high_size / view_width
    scale_y = high_size / view_height
    stroke_width = 2.0 * (scale_x + scale_y) * 0.5
    radius = stroke_width * 0.5
    canvas = Image.new("RGBA", (high_size, high_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    def point_to_pixel(point: complex) -> tuple[float, float]:
        return ((point.real - view_x) * scale_x, (point.imag - view_y) * scale_y)

    for element in root.iter():
        tag = local_tag(element)
        if tag == "path":
            parsed = parse_path(element.get("d", ""))
            for subpath in parsed.continuous_subpaths():
                points: list[tuple[float, float]] = []
                for segment in subpath:
                    segment_length = segment.length()
                    steps = max(1, math.ceil(segment_length * max(scale_x, scale_y) * 0.75))
                    start = 0 if not points else 1
                    for step in range(start, steps + 1):
                        points.append(point_to_pixel(segment.point(step / steps)))
                if len(points) >= 2:
                    draw.line(points, fill=WHITE, width=round(stroke_width), joint="curve")
                    for point in (points[0], points[-1]):
                        draw.ellipse(
                            (point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius),
                            fill=WHITE,
                        )
        elif tag == "line":
            points = [
                ((number(element, "x1") - view_x) * scale_x,
                 (number(element, "y1") - view_y) * scale_y),
                ((number(element, "x2") - view_x) * scale_x,
                 (number(element, "y2") - view_y) * scale_y),
            ]
            draw.line(points, fill=WHITE, width=round(stroke_width))
            for point in points:
                draw.ellipse((point[0] - radius, point[1] - radius,
                              point[0] + radius, point[1] + radius), fill=WHITE)
        elif tag == "circle":
            cx, cy, r = number(element, "cx"), number(element, "cy"), number(element, "r")
            box = ((cx - r - view_x) * scale_x, (cy - r - view_y) * scale_y,
                   (cx + r - view_x) * scale_x, (cy + r - view_y) * scale_y)
            draw.ellipse(box, outline=WHITE, width=round(stroke_width))
        elif tag == "ellipse":
            cx, cy = number(element, "cx"), number(element, "cy")
            rx, ry = number(element, "rx"), number(element, "ry")
            box = ((cx - rx - view_x) * scale_x, (cy - ry - view_y) * scale_y,
                   (cx + rx - view_x) * scale_x, (cy + ry - view_y) * scale_y)
            draw.ellipse(box, outline=WHITE, width=round(stroke_width))
        elif tag == "rect":
            x, y = number(element, "x"), number(element, "y")
            w, h = number(element, "width"), number(element, "height")
            box = ((x - view_x) * scale_x, (y - view_y) * scale_y,
                   (x + w - view_x) * scale_x, (y + h - view_y) * scale_y)
            rx = number(element, "rx") * min(scale_x, scale_y)
            if rx > 0:
                draw.rounded_rectangle(box, radius=rx, outline=WHITE, width=round(stroke_width))
            else:
                draw.rectangle(box, outline=WHITE, width=round(stroke_width))
        elif tag in ("polyline", "polygon"):
            raw = [float(value) for value in element.get("points", "").replace(",", " ").split()]
            points = [((raw[index] - view_x) * scale_x, (raw[index + 1] - view_y) * scale_y)
                      for index in range(0, len(raw) - 1, 2)]
            if len(points) >= 2:
                if tag == "polygon":
                    points.append(points[0])
                draw.line(points, fill=WHITE, width=round(stroke_width), joint="curve")

    image = canvas.resize((ICON_SIZE, ICON_SIZE), Image.Resampling.LANCZOS)
    # Keep visible pixels white; antialiasing is carried only by alpha.
    alpha = image.getchannel("A")
    white = Image.new("RGBA", image.size, (255, 255, 255, 0))
    white.putalpha(alpha)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    white.save(output_path, optimize=True)
    return white


def build(source_dir: Path, atlas_path: Path, index_path: Path, preview_dir: Path) -> None:
    missing = [name for name in ICON_NAMES if not (source_dir / f"{name}.svg").is_file()]
    if missing:
        paths = "\n".join(f"  {source_dir / (name + '.svg')}" for name in missing)
        raise FileNotFoundError(f"Missing Lucide SVG files:\n{paths}")

    preview_dir.mkdir(parents=True, exist_ok=True)
    columns = COLUMNS
    rows = (len(ICON_NAMES) + columns - 1) // columns
    atlas = Image.new("RGBA", (columns * CELL_SIZE, rows * CELL_SIZE), (0, 0, 0, 0))
    index: dict[str, dict[str, int]] = {}

    for position, name in enumerate(ICON_NAMES):
        row, column = divmod(position, columns)
        image = render_icon(source_dir / f"{name}.svg", preview_dir / f"{name}.png")
        x = column * CELL_SIZE + PADDING
        y = row * CELL_SIZE + PADDING
        atlas.alpha_composite(image, (x, y))
        index[name] = {"x": x, "y": y, "width": ICON_SIZE, "height": ICON_SIZE}

    atlas_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(atlas_path, optimize=True)
    index_path.write_text(
        json.dumps(
            {
                "version": 1,
                "atlas": "carbonclient:textures/gui/icons.png",
                "width": atlas.width,
                "height": atlas.height,
                "iconSize": ICON_SIZE,
                "cellSize": CELL_SIZE,
                "columns": columns,
                "rows": rows,
                "icons": index,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    license_file = source_dir / "LICENSE.txt"
    if not license_file.is_file():
        raise FileNotFoundError(f"Lucide license is required: {license_file}")
    LICENSE_PATH.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(license_file, LICENSE_PATH)

    metadata = atlas_path.with_suffix(atlas_path.suffix + ".mcmeta")
    metadata.write_text(
        json.dumps({"texture": {"blur": True, "clamp": True, "mipmaps": [1, 2, 3]}}, indent=2)
        + "\n",
        encoding="utf-8",
    )
    try:
        atlas_name = atlas_path.relative_to(ROOT)
        index_name = index_path.relative_to(ROOT)
    except ValueError:
        atlas_name, index_name = atlas_path, index_path
    print(f"Built {len(ICON_NAMES)} Lucide icons into {atlas_name}")
    print(f"Index: {index_name} ({atlas.width} x {atlas.height})")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE_DIR)
    parser.add_argument("--atlas", type=Path, default=ATLAS_PATH)
    parser.add_argument("--index", type=Path, default=INDEX_PATH)
    parser.add_argument("--preview-dir", type=Path, default=PREVIEW_DIR)
    args = parser.parse_args()
    try:
        build(args.source.resolve(), args.atlas.resolve(), args.index.resolve(), args.preview_dir.resolve())
    except Exception as failure:
        print(f"Icon build failed: {failure}", file=sys.stderr)
        raise SystemExit(1) from failure


if __name__ == "__main__":
    main()
