#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Render the deb installer's debconf dialogs to the quickstart screenshots.

Regenerates ``docs/images/install/*.png`` — the screenshots embedded in
``docs/install-deb.md`` — by running the *real* debconf dialog (whiptail)
frontend against the package's own ``debian/carlos-emr.templates`` inside a
fixed-size tmux pty, capturing each dialog with its colours, and rasterising
the terminal cells to PNG. Because the text comes from debconf itself, the
images cannot drift from what the installer actually shows: re-run this
script whenever ``debian/carlos-emr.templates`` changes.

The questions are asked in the order ``debian/carlos-emr.config`` asks them
(server-name, bind-ip, province, java-heap, tls-mode, acme-email,
reset-seed-admin), followed by the ``initial-credentials`` note that
``debian/carlos-emr.postinst`` shows after a successful seeded-admin
replacement. Two deliberate divergences from a live install, both noted in
the doc: the host-name field shows the template default (``localhost``)
rather than this machine's FQDN, and ``acme-email`` plus the final note are
rendered unconditionally so every dialog has an image.

Dependencies (Debian/Ubuntu): debconf, whiptail, tmux, python3-pil (or
``pip install pillow``), fonts-dejavu-core. Needs no root and touches no
system debconf database — everything runs against a throwaway database in a
temporary directory.

Usage:
    python3 scripts/render-debconf-screenshots.py [--out docs/images/install]

Output is deterministic for a given templates file, terminal geometry
(100x32 cells) and font (DejaVu Sans Mono, 16x32 px cells -> 1600x1024 px).
"""

import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time

from PIL import Image, ImageDraw, ImageFont

REPO = pathlib.Path(__file__).resolve().parent.parent
TEMPLATES = REPO / "debian" / "carlos-emr.templates"

COLS, ROWS = 100, 32
CELL_W, CELL_H = 16, 32
FONT_SIZE = 26
FONT_DIR = pathlib.Path("/usr/share/fonts/truetype/dejavu")
TMUX_SESSION = "carlos-debconf-shots"

# (output file name, question suffix, text unique to that dialog to wait for)
DIALOGS = [
    ("01-server-name.png", "server-name", "fully qualified host name"),
    ("02-bind-ip.png", "bind-ip", "serves every network interface"),
    ("03-province.png", "province", "provincial billing for Ontario"),
    ("04-java-heap.png", "java-heap", "will not run well below 2g"),
    ("05-tls-mode.png", "tls-mode", "selfsigned generates a certificate now"),
    ("06-acme-email.png", "acme-email", "warn you if a certificate is about to"),
    ("07-reset-seed-admin.png", "reset-seed-admin", "own test suite"),
    ("08-initial-credentials.png", "initial-credentials",
     "initial administrator password and PIN"),
]

# The classic 16-colour terminal palette — what whiptail's colours mean on a
# stock Linux terminal. Indices 0-7 normal, 8-15 bright (bold).
PALETTE = [
    (0x00, 0x00, 0x00), (0xAA, 0x00, 0x00), (0x00, 0xAA, 0x00),
    (0xAA, 0x55, 0x00), (0x00, 0x00, 0xAA), (0xAA, 0x00, 0xAA),
    (0x00, 0xAA, 0xAA), (0xAA, 0xAA, 0xAA),
    (0x55, 0x55, 0x55), (0xFF, 0x55, 0x55), (0x55, 0xFF, 0x55),
    (0xFF, 0xFF, 0x55), (0x55, 0x55, 0xFF), (0xFF, 0x55, 0xFF),
    (0x55, 0xFF, 0xFF), (0xFF, 0xFF, 0xFF),
]
DEFAULT_FG, DEFAULT_BG = 7, 0

DEBCONF_CONF = """\
Config: configdb
Templates: templatedb

Name: configdb
Driver: File
Filename: {dir}/config.dat

Name: templatedb
Driver: File
Filename: {dir}/templates.dat
"""

# Asks every dialog in installer order against the throwaway database. The
# X_LOADTEMPLATEFILE call is how dpkg-preconfigure itself loads templates.
DRIVER_SH = """\
#!/bin/sh
set -e
. /usr/share/debconf/confmodule
db_x_loadtemplatefile "$CARLOS_TEMPLATES" carlos-emr
db_title "Configuring carlos-emr"
for q in server-name bind-ip province java-heap tls-mode acme-email \\
         reset-seed-admin initial-credentials; do
    db_input high carlos-emr/$q || true
    db_go || true
done
"""


def tmux(*args, check=True):
    return subprocess.run(["tmux", *args], check=check, capture_output=True,
                          text=True)


def capture_pane():
    return tmux("capture-pane", "-ep", "-t", TMUX_SESSION).stdout


def wait_for(marker, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        content = capture_pane()
        if marker in content:
            time.sleep(0.3)  # let the frame finish drawing
            return capture_pane()
        time.sleep(0.2)
    raise TimeoutError(f"dialog containing {marker!r} never appeared")


SGR_RE = re.compile(r"\x1b\[([0-9;]*)m")


def parse_ansi(text):
    """Turn a `tmux capture-pane -e` dump into a ROWS x COLS cell grid.

    Each cell is (char, fg_index, bg_index). Handles the SGR subset whiptail
    emits: reset, bold, reverse, and the 16 standard fore/background colours.
    """
    grid = [[(" ", DEFAULT_FG, DEFAULT_BG) for _ in range(COLS)]
            for _ in range(ROWS)]
    for row, line in enumerate(text.split("\n")[:ROWS]):
        col = 0
        fg, bg, bold, reverse = DEFAULT_FG, DEFAULT_BG, False, False
        pos = 0
        while pos < len(line) and col < COLS:
            m = SGR_RE.match(line, pos)
            if m:
                for code in (int(c) for c in (m.group(1) or "0").split(";")):
                    if code == 0:
                        fg, bg, bold, reverse = (DEFAULT_FG, DEFAULT_BG,
                                                 False, False)
                    elif code == 1:
                        bold = True
                    elif code == 7:
                        reverse = True
                    elif code == 27:
                        reverse = False
                    elif 30 <= code <= 37:
                        fg = code - 30
                    elif code == 39:
                        fg = DEFAULT_FG
                    elif 40 <= code <= 47:
                        bg = code - 40
                    elif code == 49:
                        bg = DEFAULT_BG
                    elif 90 <= code <= 97:
                        fg = code - 90 + 8
                    elif 100 <= code <= 107:
                        bg = code - 100 + 8
                pos = m.end()
                continue
            ch = line[pos]
            pos += 1
            if ch == "\x1b":  # unhandled escape: skip to its final byte
                while pos < len(line) and not line[pos].isalpha():
                    pos += 1
                pos += 1
                continue
            cf, cb = (bg, fg) if reverse else (fg, bg)
            if bold and cf < 8:
                cf += 8
            grid[row][col] = (ch, cf, cb)
            col += 1
    return grid


def render(grid, out_path, font, font_bold):
    img = Image.new("RGB", (COLS * CELL_W, ROWS * CELL_H),
                    PALETTE[DEFAULT_BG])
    draw = ImageDraw.Draw(img)
    for row in range(ROWS):
        for col in range(COLS):
            ch, fg, bg = grid[row][col]
            x, y = col * CELL_W, row * CELL_H
            draw.rectangle([x, y, x + CELL_W - 1, y + CELL_H - 1],
                           fill=PALETTE[bg])
            if ch != " ":
                f = font_bold if fg >= 8 else font
                draw.text((x + CELL_W / 2, y + CELL_H / 2), ch,
                          font=f, fill=PALETTE[fg], anchor="mm")
    img.save(out_path, optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", default=str(REPO / "docs/images/install"),
                        help="output directory for the PNGs")
    args = parser.parse_args()
    out_dir = pathlib.Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    for tool in ("tmux", "whiptail", "debconf"):
        if shutil.which(tool) is None:
            sys.exit(f"error: {tool} is not installed")
    if not TEMPLATES.is_file():
        sys.exit(f"error: {TEMPLATES} not found")
    try:
        font = ImageFont.truetype(str(FONT_DIR / "DejaVuSansMono.ttf"),
                                  FONT_SIZE)
        font_bold = ImageFont.truetype(
            str(FONT_DIR / "DejaVuSansMono-Bold.ttf"), FONT_SIZE)
    except OSError:
        sys.exit("error: DejaVu Sans Mono not found (install fonts-dejavu-core)")

    with tempfile.TemporaryDirectory(prefix="carlos-debconf-") as tmp:
        tmp = pathlib.Path(tmp)
        (tmp / "debconf.conf").write_text(DEBCONF_CONF.format(dir=tmp))
        (tmp / "driver.sh").write_text(DRIVER_SH)

        tmux("kill-session", "-t", TMUX_SESSION, check=False)
        # -u forces UTF-8 so whiptail's line-drawing captures as box
        # characters; the throwaway DEBCONF_SYSTEMRC keeps the system
        # debconf database untouched.
        cmd = (f"env DEBCONF_SYSTEMRC={tmp}/debconf.conf "
               f"DEBIAN_FRONTEND=dialog DEBIAN_PRIORITY=low "
               f"LC_ALL=C.UTF-8 CARLOS_TEMPLATES={TEMPLATES} "
               f"debconf -fdialog sh {tmp}/driver.sh; sleep 60")
        subprocess.run(["tmux", "-u", "new-session", "-d", "-s", TMUX_SESSION,
                        "-x", str(COLS), "-y", str(ROWS), cmd], check=True)
        try:
            for name, question, marker in DIALOGS:
                dump = wait_for(marker)
                render(parse_ansi(dump), out_dir / name, font, font_bold)
                print(f"rendered {out_dir / name}  (carlos-emr/{question})")
                tmux("send-keys", "-t", TMUX_SESSION, "Enter")
        finally:
            tmux("kill-session", "-t", TMUX_SESSION, check=False)


if __name__ == "__main__":
    main()
