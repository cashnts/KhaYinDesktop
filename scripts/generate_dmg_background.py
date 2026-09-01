#!/usr/bin/env python3
import math
import os
import subprocess
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

def get_font(size, bold=False):
    candidates = [
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/HelveticaNeue.ttc",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Helvetica.ttc",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()

def generate_background(width, height, scale=1, app_name="KhaYin", output_path="background.png"):
    w = width * scale
    h = height * scale

    # 1. Clean crisp white background
    img = Image.new("RGBA", (w, h), (255, 255, 255, 255))
    draw = ImageDraw.Draw(img)

    # 2. Header Typography
    title_font = get_font(int(22 * scale), bold=True)
    sub_font = get_font(int(13 * scale), bold=False)

    title_text = f"Install {app_name}"
    sub_text = f"Drag {app_name} to Applications to complete installation"

    # Draw Title (Dark Charcoal #111827)
    title_bbox = draw.textbbox((0, 0), title_text, font=title_font)
    title_w = title_bbox[2] - title_bbox[0]
    title_x = (w - title_w) // 2
    title_y = int(48 * scale)
    draw.text((title_x, title_y), title_text, fill=(17, 24, 39, 255), font=title_font)

    # Draw Subtitle (Subtle Gray #6B7280)
    sub_bbox = draw.textbbox((0, 0), sub_text, font=sub_font)
    sub_w = sub_bbox[2] - sub_bbox[0]
    sub_x = (w - sub_w) // 2
    sub_y = int(78 * scale)
    draw.text((sub_x, sub_y), sub_text, fill=(107, 114, 128, 255), font=sub_font)

    # 3. Clean minimal directional arrow between icons
    arrow_start_x = int(280 * scale)
    arrow_end_x = int(380 * scale)
    arrow_y = int(195 * scale)

    # Subtle dotted connector line
    num_dots = 6
    for i in range(num_dots):
        t = i / (num_dots - 1)
        dot_x = int(arrow_start_x + t * (arrow_end_x - arrow_start_x - int(12 * scale)))
        dot_r = int(2.5 * scale)
        draw.ellipse(
            [dot_x - dot_r, arrow_y - dot_r, dot_x + dot_r, arrow_y + dot_r],
            fill=(156, 163, 175, 200)
        )

    # Minimal arrow head
    head_size = int(10 * scale)
    head_x = arrow_end_x
    head_points = [
        (head_x - head_size, arrow_y - head_size),
        (head_x, arrow_y),
        (head_x - head_size, arrow_y + head_size),
        (head_x - int(head_size * 0.4), arrow_y),
    ]
    draw.polygon(head_points, fill=(107, 114, 128, 230))

    img.save(output_path, "PNG")
    print(f"Generated {output_path} ({w}x{h})")

def main():
    app_name = sys.argv[1] if len(sys.argv) > 1 else "KhaYin"
    out_dir = sys.argv[2] if len(sys.argv) > 2 else "."
    os.makedirs(out_dir, exist_ok=True)

    bg_1x = os.path.join(out_dir, "background.png")
    bg_2x = os.path.join(out_dir, "background@2x.png")
    bg_tiff = os.path.join(out_dir, "background.tiff")

    generate_background(660, 400, scale=1, app_name=app_name, output_path=bg_1x)
    generate_background(660, 400, scale=2, app_name=app_name, output_path=bg_2x)

    try:
        subprocess.run(
            ["tiffutil", "-cathidpicheck", bg_1x, bg_2x, "-out", bg_tiff],
            check=True,
            capture_output=True,
        )
        print(f"Generated Retina TIFF: {bg_tiff}")
    except Exception as e:
        print(f"tiffutil failed ({e}), using PNG directly.")

if __name__ == "__main__":
    main()
