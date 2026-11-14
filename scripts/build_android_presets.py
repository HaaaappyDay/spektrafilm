#!/usr/bin/env python3
"""Bake committed mobile presets; writes artifacts only under --out.

python scripts/build_android_presets.py --out android/app/src/main/assets/presets
Requires Python 3.13 and the project's dependencies, never invoked by Gradle.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict
import hashlib
import importlib.metadata
import json
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import re

from spektrafilm_lut_creator.bundles import BundleSpec
from spektrafilm_lut_creator.builders import BundleBuilder

ROOT = Path(__file__).resolve().parents[1]
PRESETS = {
    'kodak_portra_400': 'Kodak Portra 400',
    'kodak_gold_200': 'Kodak Gold 200',
    'kodak_ektar_100': 'Kodak Ektar 100',
}
ATTRIBUTION = 'Generated from spektrafilm by Andrea Volpato and contributors; https://github.com/andreavolpato/spektrafilm. See LICENSE.txt.'


def make_spec(film: str) -> BundleSpec:
    if film not in PRESETS:
        raise ValueError('Unknown mobile preset')
    return BundleSpec(
        film_profile=film, print_profiles=('kodak_portra_endura',),
        input_color_space='sRGB', output_color_space='sRGB', topology='1lut',
        resolution=33, stops_above_midgray=None, qa=True,
    )


def build(out: Path) -> dict:
    out = out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    manifest = {
        'schemaVersion': 1,
        'engineCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'generator': {'name': 'build_android_presets.py', 'version': 1,
                      'sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                      'python': platform.python_version(),
                      'dependencies': {d: importlib.metadata.version(d) for d in ('spektrafilm', 'numpy', 'scipy', 'colour-science', 'numba', 'Pillow')}},
        'bakeSpec': {}, 'presets': [],
    }
    for film, display_name in PRESETS.items():
        spec = make_spec(film)
        builder = BundleBuilder(spec)
        bundle = builder.build()
        # QA renders figures in a temporary subtree of --out. Retain its complete
        # numerical text/metadata, not 30MB of developer plots in the APK.
        report_dir = out / 'bake-reports' / film
        report_dir.mkdir(parents=True, exist_ok=True)
        cube = out / f'{film}.cube'
        with tempfile.TemporaryDirectory(prefix='.bake-', dir=out) as work:
            bundle_dir = Path(work)
            builder.write(bundle, bundle_dir)
            shutil.copyfile(bundle_dir / bundle.luts[0][0], cube)
            shutil.copyfile(bundle_dir / 'bundle.json', report_dir / 'bundle.json')
            for report in (bundle_dir / 'qa').rglob('report.md'):
                text = re.sub(r'^!\[.*?\]\(.*?\)\s*$', '', report.read_text(), flags=re.MULTILINE)
                (report_dir / 'report.md').write_text(text + '\nQA figures omitted from mobile assets; all numerical results retained.\n')
        manifest['bakeSpec'][film] = asdict(spec)
        lut = bundle.luts[0][1]
        manifest['presets'].append({
            'id': film, 'displayName': display_name, 'assetPath': f'presets/{cube.name}',
            'sha256': hashlib.sha256(cube.read_bytes()).hexdigest(),
            'filmProfile': film, 'printProfile': 'kodak_portra_endura',
            'inputColorSpace': 'sRGB', 'outputColorSpace': 'sRGB', 'resolution': 33,
            'domainMin': list(lut.domain_min), 'domainMax': list(lut.domain_max),
            'attribution': ATTRIBUTION,
        })
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    shutil.copyfile(ROOT / 'SPEKTRAFILM_LICENSE.txt', out / 'LICENSE.txt')
    (out / 'CHANGELOG.txt').write_text(
        'Android v1 generated assets, schema 1\n' + ATTRIBUTION + '\n'
        'Generated/modified artifact: three 33^3 sRGB-to-sRGB combined film/print LUTs.\n'
        'Explicit native SDR headroom (stops_above_midgray=None); no automatic, spatial or stochastic effects.\n'
        'Runtime/model source unchanged. Full effective settings, source commit and dependency versions: manifest.json.\n'
        'The project source uses GPLv3; this directory preserves the separate asset license verbatim in LICENSE.txt.\n')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    build(parser.parse_args().out)
