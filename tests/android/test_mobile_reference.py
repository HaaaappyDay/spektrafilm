import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import itertools
import numpy as np
import pytest
from verify_android_color import sample_cube, transform, decode_srgb
from build_android_presets import make_spec, PRESETS


def identity(n=3):
    b, g, r = np.meshgrid(*([np.linspace(0, 1, n)] * 3), indexing='ij')
    return np.stack((r, g, b), axis=-1)


def test_identity_and_channel_order():
    rgb = np.random.default_rng(7639).random((300, 3))
    np.testing.assert_allclose(sample_cube(identity(), rgb), rgb, atol=1e-14)
    np.testing.assert_allclose(sample_cube(identity()[..., ::-1], rgb), rgb[..., ::-1], atol=1e-14)
    for strength in (0, .5, 1):
        np.testing.assert_allclose(transform(rgb, identity(), strength, 0, 0), rgb, atol=1e-14)


def test_domains_corners_and_clamping():
    points = np.array(list(itertools.product((-1., 1.), repeat=3)))
    np.testing.assert_allclose(sample_cube(identity(2), points, (-1, -1, -1), (1, 1, 1)), (points + 1) / 2)
    np.testing.assert_allclose(sample_cube(identity(), np.array([[-2, .5, 3]])), [[0, .5, 1]])


def test_zero_strength_keeps_exposure_and_temperature():
    rgb = np.array([[.2, .2, .2], [.4, .4, .4]])
    black = np.zeros((2, 2, 2, 3))
    ev = transform(rgb, black, 0, 1, 0)
    np.testing.assert_allclose(decode_srgb(ev), 2 * decode_srgb(rgb), atol=1e-14)
    warm = transform(rgb, black, 0, 0, 100)
    assert np.all(warm[:, 0] > rgb[:, 0]); assert np.all(warm[:, 2] < rgb[:, 2])
    np.testing.assert_allclose(decode_srgb(warm) / decode_srgb(rgb), np.tile([2**.25, 1, 2**-.25], (2, 1)))


@pytest.mark.parametrize('params', [(float('nan'), 0, 0), (1, float('inf'), 0), (-.1, 0, 0), (1.1, 0, 0), (1, 2.1, 0), (1, 0, 101)])
def test_invalid_parameters_fail(params):
    with pytest.raises(ValueError): transform(np.array([[.5]*3]), identity(), *params)


def test_bake_settings_are_explicit():
    assert set(PRESETS) == {'kodak_portra_400', 'kodak_gold_200', 'kodak_ektar_100'}
    for film in PRESETS:
        spec = make_spec(film)
        assert spec.print_profiles == ('kodak_portra_endura',)
        assert spec.stops_above_midgray is None and spec.qa is True
        assert spec.resolution == 33 and spec.topology == '1lut'
        assert spec.input_color_space == spec.output_color_space == 'sRGB'
        assert spec.input_gamut_compress.active
        assert spec.output_gamut_compress.algorithm == 'cam16ucs'


def test_committed_presets_manifest_and_assets():
    import hashlib, json
    from dataclasses import asdict
    from spektrafilm_lut_creator.formats import get_format
    root = Path(__file__).resolve().parents[2]
    assets = root / 'android/app/src/main/assets/presets'
    manifest = json.loads((assets / 'manifest.json').read_text())
    assert manifest['schemaVersion'] == 1
    assert len(manifest['engineCommit']) == 40
    assert len(manifest['presets']) == 3
    hashes = set()
    for preset in manifest['presets']:
        film = preset['id']
        assert film in PRESETS
        # JSON turns tuple fields into lists.
        assert manifest['bakeSpec'][film] == json.loads(json.dumps(asdict(make_spec(film))))
        path = assets.parent / preset['assetPath']
        assert path.parent == assets
        assert hashlib.sha256(path.read_bytes()).hexdigest() == preset['sha256']
        hashes.add(preset['sha256'])
        cube = get_format('cube').read(path)
        assert cube.table.shape == (33, 33, 33, 3)
        assert np.all(np.isfinite(cube.table)) and np.all((cube.table >= 0) & (cube.table <= 1))
        assert preset['domainMin'] == [0, 0, 0] and preset['domainMax'] == [1, 1, 1]
        assert not np.allclose(cube.table, identity(33), atol=.01)
    assert len(hashes) == 3
    assert (assets / 'LICENSE.txt').read_bytes() == (root / 'SPEKTRAFILM_LICENSE.txt').read_bytes()
