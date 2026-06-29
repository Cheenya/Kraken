#!/usr/bin/env python3
from __future__ import annotations

import json
import struct
import sys
import zlib
from pathlib import Path


OUTPUT_SIZE = (1170, 2532)
OUTPUT_NAME = "kraken-ios-launch-reference.png"


def paeth_predictor(left: int, above: int, upper_left: int) -> int:
    estimate = left + above - upper_left
    distance_left = abs(estimate - left)
    distance_above = abs(estimate - above)
    distance_upper_left = abs(estimate - upper_left)
    if distance_left <= distance_above and distance_left <= distance_upper_left:
        return left
    if distance_above <= distance_upper_left:
        return above
    return upper_left


def parse_png(path: Path) -> tuple[int, int, list[tuple[int, int, int, int]]]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")

    offset = 8
    width = height = color_type = None
    compressed = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(">IIBBBBB", chunk_data)
            if bit_depth != 8 or compression != 0 or filter_method != 0 or interlace != 0:
                raise ValueError(f"unsupported PNG format in {path}")
            if color_type not in {2, 6}:
                raise ValueError(f"unsupported PNG color type {color_type} in {path}")
        elif chunk_type == b"IDAT":
            compressed.extend(chunk_data)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None or color_type is None:
        raise ValueError(f"missing IHDR in {path}")

    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(bytes(compressed))
    pixels: list[tuple[int, int, int, int]] = []
    previous = bytearray(stride)
    position = 0
    for _ in range(height):
        filter_type = raw[position]
        position += 1
        scanline = bytearray(raw[position:position + stride])
        position += stride
        for index in range(stride):
            left = scanline[index - channels] if index >= channels else 0
            above = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                scanline[index] = (scanline[index] + left) & 0xFF
            elif filter_type == 2:
                scanline[index] = (scanline[index] + above) & 0xFF
            elif filter_type == 3:
                scanline[index] = (scanline[index] + ((left + above) // 2)) & 0xFF
            elif filter_type == 4:
                scanline[index] = (scanline[index] + paeth_predictor(left, above, upper_left)) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"unsupported PNG filter {filter_type} in {path}")
        previous = scanline
        for index in range(0, stride, channels):
            red = scanline[index]
            green = scanline[index + 1]
            blue = scanline[index + 2]
            alpha = scanline[index + 3] if channels == 4 else 255
            pixels.append((red, green, blue, alpha))
    return width, height, pixels


def write_png(path: Path, width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> None:
    rows = bytearray()
    for row in range(height):
        rows.append(0)
        start = row * width
        for red, green, blue, alpha in pixels[start:start + width]:
            rows.extend((red, green, blue, alpha))
    payload = zlib.compress(bytes(rows), level=9)

    def chunk(kind: bytes, body: bytes) -> bytes:
        crc = zlib.crc32(kind)
        crc = zlib.crc32(body, crc)
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", crc & 0xFFFFFFFF)

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
    png.extend(chunk(b"IDAT", payload))
    png.extend(chunk(b"IEND", b""))
    path.write_bytes(bytes(png))


def asset_color(repo_root: Path) -> tuple[int, int, int]:
    contents_path = repo_root / "app-ios/KrakenIOS/Assets.xcassets/BrandBackground.colorset/Contents.json"
    contents = json.loads(contents_path.read_text(encoding="utf-8"))
    components = contents["colors"][0]["color"]["components"]
    return tuple(int(components[channel], 16) for channel in ("red", "green", "blue"))


def composite_launch_reference(repo_root: Path) -> Path:
    background = asset_color(repo_root)
    width, height = OUTPUT_SIZE
    canvas = [(background[0], background[1], background[2], 255)] * (width * height)

    glyph_path = repo_root / "app-ios/KrakenIOS/Assets.xcassets/LaunchGlyph.imageset/launch-glyph-360.png"
    glyph_width, glyph_height, glyph_pixels = parse_png(glyph_path)
    origin_x = (width - glyph_width) // 2
    origin_y = (height - glyph_height) // 2
    for y in range(glyph_height):
        for x in range(glyph_width):
            source = glyph_pixels[y * glyph_width + x]
            alpha = source[3] / 255.0
            if alpha == 0:
                continue
            target_index = (origin_y + y) * width + origin_x + x
            target = canvas[target_index]
            canvas[target_index] = (
                round(source[0] * alpha + target[0] * (1 - alpha)),
                round(source[1] * alpha + target[1] * (1 - alpha)),
                round(source[2] * alpha + target[2] * (1 - alpha)),
                255,
            )

    output_path = repo_root / "artifacts/ios-smoke" / OUTPUT_NAME
    output_path.parent.mkdir(parents=True, exist_ok=True)
    write_png(output_path, width, height, canvas)
    return output_path


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    output_path = composite_launch_reference(repo_root)
    print(output_path.relative_to(repo_root))
    return 0


if __name__ == "__main__":
    sys.exit(main())
