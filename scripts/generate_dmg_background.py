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

    # 1. Base ultra-sleek dark gradient backdrop (#0C0D12 -> #12131C)
    img = Image.new("RGBA", (w, h), (12, 13, 18, 255))
    draw = ImageDraw.Draw(img)

    # 2. Ambient Nebula Glow Layer
    glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_layer)

    # Left purple/indigo ambient aura (under App icon)
    cx1, cy1 = int(175 * scale), int(195 * scale)
    r1 = int(130 * scale)
    for r in range(r1, 0, -int(8 * scale)):
        alpha = int(48 * (1 - (r / r1) ** 1.3))
        glow_draw.ellipse(
            [cx1 - r, cy1 - r, cx1 + r, cy1 + r],
            fill=(115, 80, 255, alpha)
        )

    # Right cyan/azure ambient aura (under Applications icon)
    cx2, cy2 = int(485 * scale), int(195 * scale)
    r2 = int(130 * scale)
    for r in range(r2, 0, -int(8 * scale)):
        alpha = int(40 * (1 - (r / r2) ** 1.3))
        glow_draw.ellipse(
            [cx2 - r, cy2 - r, cx2 + r, cy2 + r],
            fill=(0, 190, 255, alpha)
        )

    # Center connector flow glow
    cx3, cy3 = int(330 * scale), int(185 * scale)
    r3 = int(90 * scale)
    for r in range(r3, 0, -int(8 * scale)):
        alpha = int(25 * (1 - (r / r3) ** 1.2))
        glow_draw.ellipse(
            [cx3 - r, cy3 - r, cx3 + r, cy3 + r],
            fill=(140, 120, 255, alpha)
        )

    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=int(22 * scale)))
    img = Image.alpha_composite(img, glow_layer)
    draw = ImageDraw.Draw(img)

    # 3. Soft Frosted Pedestal Pads (Generous sizing: 164x160 with smooth 28px corners)
    # Left pedestal: centered around X=175, Y=200
    # Right pedestal: centered around X=485, Y=200
    ped_w, ped_h = int(164 * scale), int(160 * scale)
    ped_r = int(26 * scale)

    for cx, cy, is_left in [(int(175 * scale), int(195 * scale), True), (int(485 * scale), int(195 * scale), False)]:
        ped_box = [cx - ped_w // 2, cy - ped_h // 2, cx + ped_w // 2, cy + ped_h // 2]
        
        # Soft outer shadow/glow
        shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        shadow_draw = ImageDraw.Draw(shadow)
        shadow_draw.rounded_rectangle(
            [ped_box[0] - int(4 * scale), ped_box[1] - int(4 * scale), ped_box[2] + int(4 * scale), ped_box[3] + int(4 * scale)],
            radius=ped_r + int(4 * scale),
            fill=(115, 80, 255, 30) if is_left else (0, 190, 255, 30)
        )
        shadow = shadow.filter(ImageFilter.GaussianBlur(radius=int(6 * scale)))
        img = Image.alpha_composite(img, shadow)

        # Frosted glass surface
        surf = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        surf_draw = ImageDraw.Draw(surf)
        surf_draw.rounded_rectangle(
            ped_box,
            radius=ped_r,
            fill=(255, 255, 255, 8),
            outline=(255, 255, 255, 32),
            width=int(1.5 * scale)
        )
        img = Image.alpha_composite(img, surf)
        draw = ImageDraw.Draw(img)

    # 4. Animated-style Glowing Flow Arrow between pedestals
    arrow_start_x = int(268 * scale)
    arrow_end_x = int(392 * scale)
    arrow_y = int(185 * scale)

    arrow_glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    arrow_glow_draw = ImageDraw.Draw(arrow_glow)

    # Glowing dotted path with gradient sizing and colors
    num_dots = 8
    for i in range(num_dots):
        t = i / (num_dots - 1)
        dot_x = int(arrow_start_x + t * (arrow_end_x - arrow_start_x - int(16 * scale)))
        dot_r = int((2.8 + 1.6 * t) * scale)
        r = int(120 + t * (0 - 120))
        g = int(95 + t * (215 - 95))
        b = int(255 + t * (255 - 255))
        alpha = int(160 + t * 90)
        arrow_glow_draw.ellipse(
            [dot_x - dot_r, arrow_y - dot_r, dot_x + dot_r, arrow_y + dot_r],
            fill=(r, g, b, alpha)
        )

    # Arrow Head `>` at arrow_end_x
    head_size = int(13 * scale)
    head_x = arrow_end_x
    head_points = [
        (head_x - head_size, arrow_y - head_size),
        (head_x, arrow_y),
        (head_x - head_size, arrow_y + head_size),
        (head_x - int(head_size * 0.45), arrow_y),
    ]
    arrow_glow_draw.polygon(head_points, fill=(0, 225, 255, 245))

    # Glow blur
    arrow_blur = arrow_glow.filter(ImageFilter.GaussianBlur(radius=int(2.5 * scale)))
    img = Image.alpha_composite(img, arrow_blur)
    img = Image.alpha_composite(img, arrow_glow)
    draw = ImageDraw.Draw(img)

    # 5. Header Typography (SF Pro style, refined hierarchy)
    title_font = get_font(int(22 * scale), bold=True)
    sub_font = get_font(int(12 * scale), bold=False)

    title_text = f"Install {app_name}"
    sub_text = f"Drag {app_name} to Applications to complete installation"

    # Draw Title
    title_bbox = draw.textbbox((0, 0), title_text, font=title_font)
    title_w = title_bbox[2] - title_bbox[0]
    title_x = (w - title_w) // 2
    title_y = int(42 * scale)
    draw.text((title_x, title_y), title_text, fill=(255, 255, 255, 245), font=title_font)

    # Draw Subtitle
    sub_bbox = draw.textbbox((0, 0), sub_text, font=sub_font)
    sub_w = sub_bbox[2] - sub_bbox[0]
    sub_x = (w - sub_w) // 2
    sub_y = int(72 * scale)
    draw.text((sub_x, sub_y), sub_text, fill=(155, 162, 185, 210), font=sub_font)

    # 6. Bottom Brand Tagline (Properly positioned above bottom margin)
    bottom_font = get_font(int(10 * scale), bold=False)
    bottom_text = "✦ Powered by KhaYin High-Performance Engine ✦"
    b_bbox = draw.textbbox((0, 0), bottom_text, font=bottom_font)
    b_w = b_bbox[2] - b_bbox[0]
    b_x = (w - b_w) // 2
    b_y = int(356 * scale)
    draw.text((b_x, b_y), bottom_text, fill=(115, 122, 142, 170), font=bottom_font)

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
