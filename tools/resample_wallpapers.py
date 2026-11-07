"""
CleanYourTV - Wallpaper Resampling Tool
Resamples images to 1280x720 JPEG (RGB_565 compliant) for optimal TV RAM usage.
"""

import sys
import os
from PIL import Image

TARGET_RES = (1280, 720)

def resample_wallpaper(src_file, dest_file):
    if not os.path.exists(src_file):
        print(f"Error: Source file '{src_file}' does not exist.")
        return False
    
    with Image.open(src_file) as im:
        im_rgb = im.convert("RGB")
        im_resized = im_rgb.resize(TARGET_RES, Image.Resampling.LANCZOS)
        im_resized.save(dest_file, "JPEG", quality=88, optimize=True)
        print(f"Resampled: {src_file} -> {dest_file} (1280x720)")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python resample_wallpapers.py <input_image> <output_image.jpg>")
        sys.exit(1)
    resample_wallpaper(sys.argv[1], sys.argv[2])

