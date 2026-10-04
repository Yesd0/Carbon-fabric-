#!/usr/bin/env python3
"""Build static Inter weight files and Carbon font providers from the official variable TTF.

Requires: python -m pip install -r tools/requirements-fonts.txt
Source font: tools/font-src/Inter[opsz,wght].ttf (SIL Open Font License).
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/font-src/Inter[opsz,wght].ttf"
OUTPUT = ROOT / "src/main/resources/assets/carbonclient/font"
WEIGHTS = (
    ("regular", "Regular", 400),
    ("medium", "Medium", 500),
    ("semibold", "SemiBold", 600),
    ("bold", "Bold", 700),
)
BASE_SIZE = 16
OVERSAMPLE = 8


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


def write_provider(font_key: str, output: Path) -> None:
    provider = {
        "providers": [
            {
                "type": "ttf",
                "file": f"carbonclient:font/inter_{font_key}.ttf",
                "shift": [0.0, 0.0],
                "size": BASE_SIZE,
                "oversample": OVERSAMPLE,
            },
            {"type": "reference", "id": "minecraft:default"},
        ]
    }
    path = output / f"carbon_inter_{font_key}.json"
    path.write_text(json.dumps(provider, indent=2) + "\n", encoding="utf-8")


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
        write_provider(font_key, output)
        print(f"Wrote {font_path.relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    build(args.source.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
