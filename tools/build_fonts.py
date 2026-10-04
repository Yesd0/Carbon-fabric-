#!/usr/bin/env python3
"""Build Carbon's static Inter weights and reliable bitmap font providers.

Requires: python -m pip install -r tools/requirements-fonts.txt
Source font: tools/font-src/Inter[opsz,wght].ttf (SIL Open Font License).
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/font-src/Inter[opsz,wght].ttf"
OUTPUT = ROOT / "src/main/resources/assets/carbonclient/font"
WEIGHTS = (
    ("regular", "Regular", 400),
    ("medium", "Medium", 500),
    ("semibold", "SemiBold", 600),
    ("bold", "Bold", 700),
)

# Rasterized at 2.25x into alpha atlases. Minecraft's bitmap provider avoids
# grayscale TTF atlas glitches seen with some shader/render backends.
BITMAP_COLUMNS = 19
BITMAP_ROWS = 5
CELL_WIDTH = 40
CELL_HEIGHT = 40
RASTER_SIZE = 36
BASELINE = 32
BITMAP_HEIGHT = 16
BITMAP_ASCENT = 13


def set_font_names(font: TTFont, style: str, weight: int) -> None:
    names = font["name"]
    values = {
        1: "Inter",
        2: style,
        3: f"Google Fonts:Inter:4.1:wght{weight}",
        4: f"Inter {style}",
        6: f"Inter-{style}",
        16: "Inter",
        17: style,
    }
    for name_id, value in values.items():
        names.setName(value, name_id, 3, 1, 0x409)
        names.setName(value, name_id, 1, 0, 0)


def write_bitmap_provider(font_key: str, font_path: Path, output: Path) -> None:
    texture_directory = output.parent / "textures" / "font"
    texture_directory.mkdir(parents=True, exist_ok=True)
    texture_path = texture_directory / f"inter_{font_key}.png"
    image = Image.new(
        "RGBA",
        (BITMAP_COLUMNS * CELL_WIDTH, BITMAP_ROWS * CELL_HEIGHT),
        (0, 0, 0, 0),
    )
    draw = ImageDraw.Draw(image)
    raster_font = ImageFont.truetype(str(font_path), RASTER_SIZE)

    for index, codepoint in enumerate(range(32, 127)):
        character = chr(codepoint)
        if character == " ":
            continue
        cell_x = (index % BITMAP_COLUMNS) * CELL_WIDTH
        cell_y = (index // BITMAP_COLUMNS) * CELL_HEIGHT
        draw.text(
            (cell_x + 2, cell_y + BASELINE),
            character,
            font=raster_font,
            fill=(255, 255, 255, 255),
            anchor="ls",
        )
    image.save(texture_path, optimize=True)

    characters = [
        "".join(chr(codepoint) for codepoint in range(start, start + BITMAP_COLUMNS))
        for start in range(32, 127, BITMAP_COLUMNS)
    ]
    provider = {
        "providers": [
            {"type": "space", "advances": {" ": 4}},
            {
                "type": "bitmap",
                "file": f"carbonclient:font/inter_{font_key}.png",
                "ascent": BITMAP_ASCENT,
                "height": BITMAP_HEIGHT,
                "chars": characters,
            },
            {"type": "reference", "id": "minecraft:default"},
        ]
    }
    (output / f"carbon_inter_{font_key}.json").write_text(
        json.dumps(provider, indent=2, ensure_ascii=True) + "\n", encoding="utf-8"
    )
    print(f"Wrote {texture_path.relative_to(ROOT)}")


def build(source: Path, output: Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(f"Inter variable font not found: {source}")
    output.mkdir(parents=True, exist_ok=True)
    for font_key, style, weight in WEIGHTS:
        font = TTFont(source)
        instance = instantiateVariableFont(
            font,
            {"opsz": 16, "wght": weight},
            inplace=True,
            optimize=True,
        )
        set_font_names(instance, style, weight)
        font_path = output / f"inter_{font_key}.ttf"
        instance.save(font_path)
        write_bitmap_provider(font_key, font_path, output)
        print(f"Wrote {font_path.relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    build(args.source.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
