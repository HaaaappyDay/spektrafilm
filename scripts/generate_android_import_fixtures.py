#!/usr/bin/env python3
"""Generate original synthetic import fixtures. Requires Pillow 12.1 + system LittleCMS2.

Run: uv run --no-project --python 3.13 --with pillow==12.1.0 scripts/generate_android_import_fixtures.py
Only writes to --out. Ordinary Android builds use committed fixtures, never this script.
"""
import argparse
import ctypes as C
import ctypes.util
import hashlib
import io
import json
import struct
import shutil
from pathlib import Path
from PIL import Image, ImageCms, ImageDraw, ImageOps


def display_p3_profile():
    """Create a matrix/TRC Display-P3 ICC from published chromaticities, no borrowed asset."""
    lib = C.CDLL(ctypes.util.find_library('lcms2'))
    class xyY(C.Structure):
        _fields_ = [('x', C.c_double), ('y', C.c_double), ('Y', C.c_double)]
    class Primaries(C.Structure):
        _fields_ = [('red', xyY), ('green', xyY), ('blue', xyY)]
    lib.cmsBuildParametricToneCurve.argtypes = [C.c_void_p, C.c_int, C.POINTER(C.c_double)]
    lib.cmsBuildParametricToneCurve.restype = C.c_void_p
    lib.cmsCreateRGBProfile.argtypes = [C.POINTER(xyY), C.POINTER(Primaries), C.POINTER(C.c_void_p)]
    lib.cmsCreateRGBProfile.restype = C.c_void_p
    lib.cmsSaveProfileToMem.argtypes = [C.c_void_p, C.c_void_p, C.POINTER(C.c_uint)]
    lib.cmsCloseProfile.argtypes = [C.c_void_p]
    lib.cmsFreeToneCurve.argtypes = [C.c_void_p]
    curve = lib.cmsBuildParametricToneCurve(None, 4,
        (C.c_double * 5)(2.4, 1 / 1.055, .055 / 1.055, 1 / 12.92, .04045))
    profile = lib.cmsCreateRGBProfile(C.byref(xyY(.3127, .3290, 1)),
        C.byref(Primaries(xyY(.680, .320, 1), xyY(.265, .690, 1), xyY(.150, .060, 1))),
        (C.c_void_p * 3)(curve, curve, curve))
    assert profile and curve
    try:
        size = C.c_uint()
        assert lib.cmsSaveProfileToMem(profile, None, C.byref(size))
        data = C.create_string_buffer(size.value)
        assert lib.cmsSaveProfileToMem(profile, data, C.byref(size))
        return normalize_profile(data.raw)
    finally:
        lib.cmsCloseProfile(profile)
        lib.cmsFreeToneCurve(curve)


def normalize_profile(data):
    result = bytearray(data)
    result[24:36] = struct.pack('>6H', 2026, 1, 1, 0, 0, 0)
    result[84:100] = b'\0' * 16
    return bytes(result)


def generate(out, native_gainmap=None):
    out.mkdir(parents=True, exist_ok=True)
    image = Image.new('RGB', (80, 60))
    draw = ImageDraw.Draw(image)
    for box, color in [((0, 0, 39, 29), (220, 30, 40)), ((40, 0, 79, 29), (30, 200, 50)),
                       ((0, 30, 39, 59), (30, 40, 220)), ((40, 30, 79, 59), (220, 200, 30))]:
        draw.rectangle(box, fill=color)
    draw.rectangle((10, 2, 15, 6), fill='white')
    orientations = []
    for orientation in range(1, 9):
        name = f'orientation-{orientation}.jpg'
        exif = Image.Exif(); exif[274] = orientation
        image.save(out / name, quality=100, subsampling=0, exif=exif)
        with Image.open(out / name) as encoded:
            normalized = ImageOps.exif_transpose(encoded)
            w, h = normalized.size
            points = [(w//4, h//4), (3*w//4, h//4), (w//4, 3*h//4), (3*w//4, 3*h//4)]
            orientations.append(dict(file=name, width=w, height=h,
                corners=[normalized.getpixel(p) for p in points]))
    transparent = Image.new('RGBA', (4, 1))
    transparent.putdata([(255, 0, 0, 128), (0, 255, 0, 0), (0, 0, 255, 255), (40, 80, 120, 64)])
    transparent.save(out / 'transparent.png')
    Image.new('RGB', (1, 1), (70, 120, 170)).save(out / 'tiny.png')
    Image.new('RGB', (1, 32768), (70, 120, 170)).save(out / 'extreme.png')
    for name, size in [('12mp.jpg', (4000, 3000)), ('24mp.jpg', (6000, 4000)), ('48mp.jpg', (8000, 6000))]:
        with Image.new('RGB', size, (70, 120, 170)) as large:
            large.save(out / name, quality=95)
    srgb_bytes = normalize_profile(ImageCms.ImageCmsProfile(ImageCms.createProfile('sRGB')).tobytes())
    srgb = ImageCms.ImageCmsProfile(io.BytesIO(srgb_bytes))
    p3_bytes = display_p3_profile()
    p3 = ImageCms.ImageCmsProfile(io.BytesIO(p3_bytes))
    patches = Image.new('RGB', (120, 40))
    d = ImageDraw.Draw(patches)
    colors = [(100, 140, 170), (180, 130, 100), (90, 160, 110)]
    for i, color in enumerate(colors): d.rectangle((40*i, 0, 40*i+39, 39), fill=color)
    patches.save(out / 'untagged.png')
    patches.save(out / 'srgb.png', icc_profile=srgb_bytes)
    patches.save(out / 'untagged.jpg', quality=100, subsampling=0)
    patches.save(out / 'srgb.jpg', quality=100, subsampling=0, icc_profile=srgb_bytes)
    p3_image = ImageCms.profileToProfile(patches, srgb, p3, renderingIntent=1, outputMode='RGB')
    p3_image.save(out / 'display-p3.png', icc_profile=p3_bytes)
    reference = ImageCms.profileToProfile(p3_image, p3, srgb, renderingIntent=1, outputMode='RGB')
    image.save(out / 'unsupported.webp', lossless=True)
    image.save(out / 'animated.png', save_all=True, append_images=[patches.resize(image.size)], duration=100, loop=0)
    (out / 'corrupt.jpg').write_bytes(b'not an image')
    (out / 'truncated.jpg').write_bytes((out / 'orientation-1.jpg').read_bytes()[:150])
    jpeg = (out / 'orientation-1.jpg').read_bytes()
    (out / 'truncated-pixels.jpg').write_bytes(jpeg[:len(jpeg)*4//5])
    png = (out / 'untagged.png').read_bytes()
    idat = png.index(b'IDAT')
    idat_length = struct.unpack('>I', png[idat-4:idat])[0]
    (out / 'truncated-pixels.png').write_bytes(png[:idat+4+idat_length//2])
    metadata = dict(orientations=orientations, p3Expected=[reference.getpixel((20+40*i, 20)) for i in range(3)],
                    srgbExpected=colors)
    (out / 'expected.json').write_text(json.dumps(metadata, indent=2)+'\n')
    if native_gainmap is not None:
        destination = out / 'synthetic-gainmap.jpg'
        if native_gainmap.resolve() != destination.resolve():
            shutil.copyfile(native_gainmap, destination)
    hashes = {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.iterdir()) if p.is_file() and p.name not in ('SHA256SUMS.json','README.md')}
    (out / 'SHA256SUMS.json').write_text(json.dumps(hashes, indent=2)+'\n')
    (out / 'README.md').write_text('''# Import fixtures

Original synthetic images generated by `scripts/generate_android_import_fixtures.py`.
No personal or externally sourced photographs. Source and generated fixtures are
covered by the repository GPLv3 license. Display-P3 ICC is generated using LittleCMS2
from D65, P3 primary chromaticities and the sRGB transfer function, not downloaded
from another project. Expected color patches are independently converted with
Pillow/LittleCMS; Android must match within 2/255 per channel. EXIF expectations
come from Pillow ImageOps.exif_transpose of the actual compressed JPEGs. Corner
colors are sampled well away from boundaries; dimensions and mirroring must match.

Regenerate from the repository root using the command in the generator docstring.
Python/Pillow/LCMS are regeneration tools only, not Android build dependencies.
SHA256SUMS.json records committed fixture bytes; timestamps inside generated ICC
profiles are normalized. 12/24/48 MP fixtures are highly compressible solid colors
but their encoded dimensions exercise real decoder sampling/allocation behavior.

synthetic-gainmap.jpg is generated separately by GainmapFixtureTest on API36 using
Android Gainmap + JPEG encoding: solid 32x24 RGB(100,140,170), neutral gain map,
HDR ratio2. Source run43540056192. Pass --native-gainmap /path/to/its/output.jpg to
regenerate the complete fixture set including this native sample. Android builds
use committed bytes; API29 can then test the same container as newer Android.

''')


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=Path('android/app/src/androidTest/assets/import-fixtures'))
    parser.add_argument('--native-gainmap', type=Path)
    args=parser.parse_args()
    generate(args.out, args.native_gainmap)
