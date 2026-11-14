"""Synthetic trace-table regressions; real Perfetto syntax/runtime is checked separately."""
from pathlib import Path
import sqlite3

SQL = (Path(__file__).resolve().parents[2] / "scripts/android_v1_frames.sql").read_text()


def trace():
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript("""
        CREATE TABLE slice(id INTEGER, ts INTEGER, dur INTEGER, track_id INTEGER, name TEXT, parent_id INTEGER);
        CREATE TABLE thread_track(id INTEGER, utid INTEGER);
        CREATE TABLE thread(utid INTEGER, upid INTEGER);
        CREATE TABLE process(upid INTEGER, name TEXT);
        CREATE TABLE process_track(id INTEGER, upid INTEGER);
        CREATE TABLE actual_frame_timeline_slice(id INTEGER, ts INTEGER, dur INTEGER, upid INTEGER,
          surface_frame_token INTEGER, display_frame_token INTEGER, layer_name TEXT, present_type TEXT);
        CREATE TABLE flow(slice_out INTEGER, slice_in INTEGER);
        INSERT INTO process VALUES(1, 'io.github.haaaappyday.filmeditor.validation'), (2, '/system/bin/surfaceflinger'), (3, 'other');
        INSERT INTO thread VALUES(1, 1);
        INSERT INTO thread_track VALUES(10, 1);
        INSERT INTO process_track VALUES(20, 1);
        INSERT INTO slice VALUES(1,100,40,20,'FilmPreview:7',NULL),
          (2,139,0,10,'FilmPreviewOutcome:7:accepted',NULL),
          (3,150,35,10,'Choreographer#doFrame 21',NULL),
          (4,155,25,10,'traversal',3),
          (5,160,10,10,'FilmPreviewDraw:7',4);
        INSERT INTO actual_frame_timeline_slice VALUES
          (30,150,50,1,21,22,'io.github.haaaappyday.filmeditor.validation/io.github.haaaappyday.filmeditor.MainActivity#1','On-time Present'),
          (31,190,110,2,0,22,NULL,'Late Present');
        INSERT INTO flow VALUES(30,31);
    """)
    return db


def result(db):
    rows = [dict(row) for row in db.execute(SQL)]
    assert len(rows) == 1
    return rows[0]


def test_uses_linked_surfaceflinger_display_end_not_app_completion():
    row = result(trace())
    assert row["changeId"] == 7
    assert row["handlerNs"] == 100 and row["drawNs"] == 160
    assert row["presentNs"] == 300  # app's end=200 is not presentation
    assert row["surfaceFrameToken"] == 21 and row["displayFrameToken"] == 22
    assert row["appFrameId"] == 30 and row["displayFrameId"] == 31
    assert row["flowVerified"] == 1


def test_missing_flow_preserves_draw_only_and_cannot_prove_presentation():
    db = trace(); db.execute("DELETE FROM flow")
    row = result(db)
    assert row["drawNs"] == 160
    assert row["presentNs"] is None and row["flowVerified"] == 0


def test_unrelated_process_layer_or_dropped_frame_cannot_supply_presentation():
    for mutation in ("UPDATE actual_frame_timeline_slice SET upid=3 WHERE id=30",
                     "UPDATE actual_frame_timeline_slice SET layer_name='StatusBar' WHERE id=30",
                     "UPDATE actual_frame_timeline_slice SET present_type='Dropped Frame' WHERE id=30",
                     "UPDATE actual_frame_timeline_slice SET present_type='Dropped Frame' WHERE id=31",
                     "UPDATE actual_frame_timeline_slice SET display_frame_token=99 WHERE id=31",
                     "UPDATE actual_frame_timeline_slice SET dur=-1 WHERE id=31",
                     "UPDATE slice SET parent_id=NULL WHERE id=5"):
        db = trace(); db.execute(mutation)
        row = result(db)
        assert row["presentNs"] is None and row["flowVerified"] == 0


def test_cancelled_preview_is_not_treated_as_accepted_content():
    db = trace(); db.execute("UPDATE slice SET name='FilmPreviewOutcome:7:cancelled' WHERE id=2")
    assert list(db.execute(SQL)) == []


def test_mismatched_frame_interval_and_ambiguous_flows_cannot_pass():
    for mutation in ("UPDATE actual_frame_timeline_slice SET ts=0, dur=1 WHERE id=30",
                     "INSERT INTO flow VALUES(30,31)"):
        db = trace(); db.execute(mutation)
        assert result(db)["flowVerified"] == 0
