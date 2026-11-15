"""Reversal stocks are scanned when a LUT is baked, matching the desktop GUI."""

from spektrafilm.runtime.params_builder import init_params
from spektrafilm_lut_creator.builders import finalize_lut_params


def test_reversal_scan_follows_the_film_type():
    positive = init_params("kodak_ektachrome_100", "kodak_portra_endura")
    negative = init_params("kodak_portra_400", "kodak_portra_endura")
    positive.debug.lut_mode = True
    negative.debug.lut_mode = True
    assert finalize_lut_params(positive).io.scan_film is True
    assert finalize_lut_params(negative).io.scan_film is False
