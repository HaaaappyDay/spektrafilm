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
    magenta = transform(rgb, black, 0, 0, 0, tint=100)
    assert np.all(magenta[:, 1] < rgb[:, 1]) and np.all(magenta[:, 0] > rgb[:, 0])
    gray = transform(np.array([[.05, .5, .9]]), black, 0, 0, 0, saturation=-100)
    assert np.allclose(gray, gray[:, :1])
    assert np.all(transform(np.array([[.02]*3]), black, 0, 0, 0, shadows=100) > .02)
    assert np.all(transform(np.array([[.6]*3]), black, 0, 0, 0, highlights=100) > .6)
    assert np.all(transform(np.array([[.6]*3]), black, 0, 0, 0, contrast=100) > .6)


@pytest.mark.parametrize('params', [(float('nan'), 0, 0), (1, float('inf'), 0), (-.1, 0, 0), (1.1, 0, 0), (1, 2.1, 0), (1, 0, 101)])
def test_invalid_parameters_fail(params):
    with pytest.raises(ValueError): transform(np.array([[.5]*3]), identity(), *params)


def test_bake_settings_are_explicit():
    from spektrafilm.model.stocks import FilmStocks
    from build_android_presets import film_records
    records = film_records()
    assert [record['id'] for record in records] == list(PRESETS)
    assert set(PRESETS) == {stock.value for stock in FilmStocks}
    assert list(PRESETS)[:3] == ['kodak_portra_400', 'kodak_gold_200', 'kodak_ektar_100']
    for record in records:
        spec = make_spec(record['id'])
        assert spec.print_profiles == (record['bake_print'],)
        assert spec.stops_above_midgray is None and spec.qa is True
        assert spec.resolution == 33 and spec.topology == '1lut'
        assert spec.input_color_space == spec.output_color_space == 'sRGB'
        assert spec.input_gamut_compress.active
        assert spec.output_gamut_compress.algorithm == 'cam16ucs'
        if record['scan_film']:
            assert record['print_profile'] == 'reversal-scan'
        else:
            assert record['print_profile'] == record['bake_print']


def test_committed_presets_manifest_and_assets():
    import hashlib, json
    from dataclasses import asdict
    from spektrafilm_lut_creator.formats import get_format
    from build_android_presets import film_records
    root = Path(__file__).resolve().parents[2]
    assets = root / 'android/app/src/main/assets/presets'
    manifest = json.loads((assets / 'manifest.json').read_text())
    assert manifest['schemaVersion'] == 1
    assert len(manifest['engineCommit']) == 40
    assert [preset['id'] for preset in manifest['presets']] == list(PRESETS)
    preserved = {
        'kodak_portra_400': 'c5ec5786d1ada6d9347cb6ee47c87249643bfa2eb758037fc1cd18215688ed85',
        'kodak_gold_200': 'e8729012ef0fb83f3c4c30f422da73b16af5acd99fe960dd1e0dc545fcd2ddc6',
        'kodak_ektar_100': 'e01828450e028c30f65d6bff3be8838bdc2de7ea7235a2f8d77e68c9795aa697',
    }
    hashes = set()
    for preset in manifest['presets']:
        film = preset['id']
        assert film in PRESETS
        if film in preserved:
            assert preset['sha256'] == preserved[film]
        record = next(item for item in film_records() if item['id'] == film)
        assert preset['displayName'] == record['display_name']
        assert preset['printProfile'] == record['print_profile']
        assert preset['scanFilm'] is record['scan_film']
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
    assert len(hashes) == len(PRESETS)
    assert (assets / 'LICENSE.txt').read_bytes() == (root / 'SPEKTRAFILM_LICENSE.txt').read_bytes()
