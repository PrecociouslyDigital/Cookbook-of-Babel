#!/usr/bin/env python3
"""Renders a contact sheet of the mod's tinted item sprites: every form x a few
colours, plus the bowls. Pure stdlib (the sprites are plain RGBA8 PNGs).
Legend is printed to stdout; rows on the sheet are numbered. The last row is
the bowl composite: tinted liquid (layer 1) under vanilla's bowl (layer 0)."""
import io
import pathlib
import struct
import zipfile
import zlib

HERE = pathlib.Path(__file__).parent
REPO = HERE.parent
JAR = pathlib.Path.home() / ".gradle/caches/neoformruntime/artifacts/minecraft_1.20.1_client.jar"
MOD = REPO / "mods/minecraft/src/main/resources/assets/cookbook_of_babel/textures/item"

FORM_SPRITES = {
    "whole": "clay_ball", "piece": "quartz", "slice": "paper", "shred": "string",
    "dice": "iron_nugget", "mash": "snowball", "powder": "bone_meal", "granule": "sugar",
    "paste": "white_dye", "batter": "light_gray_dye", "liquid": "potion", "foam": "firework_star",
    "gel": "slime_ball", "curd": "glowstone_dust", "solid": "iron_ingot", "gas": "phantom_membrane",
}
SAMPLES = [
    ("tomato", 0xE8501F), ("milk", 0xF6F2E4), ("chocolate", 0x5A3A22),
    ("herb", 0x4F7A3A), ("turmeric", 0xE8A93A), ("beet", 0x7A2A50),
]
BOWL_SAMPLES = [("tomato", 0xE8501F), ("milk", 0xF6F2E4), ("herb", 0x4F7A3A)]
SCALE = 6
DIGITS = {  # 3x5 pixel font
    "0": ["###", "# #", "# #", "# #", "###"], "1": ["  #", "  #", "  #", "  #", "  #"],
    "2": ["###", "  #", "###", "#  ", "###"], "3": ["###", "  #", "###", "  #", "###"],
    "4": ["# #", "# #", "###", "  #", "  #"], "5": ["###", "#  ", "###", "  #", "###"],
    "6": ["###", "#  ", "###", "# #", "###"], "7": ["###", "  #", "  #", "  #", "  #"],
    "8": ["###", "# #", "###", "# #", "###"], "9": ["###", "# #", "###", "  #", "###"],
}


def png_chunks(data):
    stream = io.BytesIO(data)
    assert stream.read(8) == b"\x89PNG\r\n\x1a\n"
    while True:
        length, kind = struct.unpack(">I4s", stream.read(8))
        payload = stream.read(length)
        (crc,) = struct.unpack(">I", stream.read(4))
        assert crc == zlib.crc32(kind + payload) & 0xFFFFFFFF
        yield kind, payload
        if kind == b"IEND":
            return


def decode_png(data):
    """Decodes a colour-type-6, bit-depth-8, non-interlaced PNG to RGBA rows."""
    header = None
    idat = b""
    for kind, payload in png_chunks(data):
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            idat += payload
    if header is None:
        raise SystemExit("PNG has no IHDR chunk")
    w, h, depth, ctype, _, _, interlace = header
    if depth != 8 or ctype != 6 or interlace != 0:
        raise SystemExit(f"unsupported PNG: depth={depth} colortype={ctype} interlace={interlace}")
    raw = zlib.decompress(idat)
    stride, bpp = w * 4, 4
    rows, prior = [], [0] * stride
    pos = 0
    for _ in range(h):
        ftype = raw[pos]
        line = list(raw[pos + 1 : pos + 1 + stride])
        pos += 1 + stride
        if ftype == 0:
            pass
        elif ftype == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prior[i]) & 255
        elif ftype == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + (left + prior[i]) // 2) & 255
        elif ftype == 4:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                up = prior[i]
                upleft = prior[i - bpp] if i >= bpp else 0
                p = left + upleft - up
                paeth = min(abs(p - left), abs(p - up), abs(p - upleft))
                if paeth == abs(p - left):
                    guess = left
                elif paeth == abs(p - up):
                    guess = up
                else:
                    guess = upleft
                line[i] = (line[i] + guess) & 255
        else:
            raise SystemExit(f"unsupported filter {ftype}")
        rows.append(line)
        prior = line
    return w, h, rows


def encode_png(w, h, rows):
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + bytes(line) for line in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def at(sprite, x, y):
    _, _, rows = sprite
    return rows[y][x * 4 : x * 4 + 4]


def tinted(sprite, rgb):
    """Multiply-tints an RGBA sprite, preserving alpha."""
    t = [(rgb >> 16 & 255) / 255, (rgb >> 8 & 255) / 255, (rgb & 255) / 255]
    _, h, rows = sprite
    out = []
    for line in rows:
        out.append([c for px in range(0, len(line), 4)
                    for c in (int(line[px] * t[0]), int(line[px + 1] * t[1]), int(line[px + 2] * t[2]), line[px + 3])])
    return (sprite[0], h, out)


def over(top, bottom):
    """Alpha-composites `top` over `bottom`, both same-size RGBA sprites."""
    w, h, _ = top
    out = [[0] * (w * 4) for _ in range(h)]
    for y in range(h):
        for x in range(w):
            tr, tg, tb, ta = (v / 255 for v in at(top, x, y))
            br, bg, bb, ba = (v / 255 for v in at(bottom, x, y))
            alpha = ta + ba * (1 - ta)
            if alpha == 0:
                continue
            out[y][x * 4 : x * 4 + 4] = [int((tr * ta + br * ba * (1 - ta)) / alpha * 255),
                                         int((tg * ta + bg * ba * (1 - ta)) / alpha * 255),
                                         int((tb * ta + bb * ba * (1 - ta)) / alpha * 255),
                                         int(alpha * 255)]
    return (w, h, out)


def blit(sheet, sprite, ox, oy, scale):
    sw, sh, _ = sprite
    for y in range(sh):
        for x in range(sw):
            rgba = at(sprite, x, y)
            if rgba[3]:
                for dy in range(scale):
                    for dx in range(scale):
                        py, px = oy + y * scale + dy, ox + x * scale + dx
                        sheet[py][px * 4 : px * 4 + 4] = rgba


def main():
    jar = zipfile.ZipFile(JAR)
    names = set(FORM_SPRITES.values()) | {"bowl"}
    sprites = {name: decode_png(jar.read(f"assets/minecraft/textures/item/{name}.png")) for name in names}
    contents = decode_png(MOD.joinpath("bowl_contents.png").read_bytes())

    grid = [[tinted(sprites[FORM_SPRITES[form]], colour) for form in FORM_SPRITES] for _, colour in SAMPLES]
    bowls = []
    for _, colour in BOWL_SAMPLES:
        bowls.append(over(tinted(contents, colour), sprites["bowl"]))

    cols, cell = len(FORM_SPRITES), 16 * SCALE
    pad, label_h = 10, 3 * SCALE + 8
    width = pad + cols * (cell + pad)
    height = pad + (len(SAMPLES) + 1) * (cell + pad + label_h)
    sheet = [[40, 40, 46, 255] * width for _ in range(height)]

    legend = [f"row {i}: {name}" for i, (name, _) in enumerate(SAMPLES)]
    legend.append(f"row {len(SAMPLES)}: bowls of " + ", ".join(n for n, _ in BOWL_SAMPLES))
    legend.append("columns: " + " ".join(f"{i}={f}" for i, f in enumerate(FORM_SPRITES)))
    print("\n".join(legend))

    def draw_num(n, ox, oy):
        for ch in str(n):
            glyph = DIGITS[ch]
            for gy, rowline in enumerate(glyph):
                for gx, on in enumerate(rowline):
                    if on != " ":
                        for dy in range(SCALE):
                            for dx in range(SCALE):
                                px = ox + gx * SCALE + dx
                                sheet[oy + gy * SCALE + dy][px * 4 : px * 4 + 4] = [220, 220, 220, 255]
            ox += 4 * SCALE

    for r, row in enumerate(grid):
        draw_num(r, pad, pad + r * (cell + pad + label_h) + cell + 4)
        for c, cell_sprite in enumerate(row):
            blit(sheet, cell_sprite, pad + c * (cell + pad), pad + r * (cell + pad + label_h), SCALE)
    bowl_row = pad + len(SAMPLES) * (cell + pad + label_h)
    draw_num(len(SAMPLES), pad, bowl_row + cell + 4)
    for c, stack in enumerate(bowls):
        blit(sheet, stack, pad + c * (cell + pad), bowl_row, SCALE)

    out = HERE / "contact_sheet.png"
    try:
        out.write_bytes(encode_png(width, height, sheet))
    except OSError as err:
        raise SystemExit(f"cannot write contact sheet: {err}") from err
    print(out)


if __name__ == "__main__":
    main()
