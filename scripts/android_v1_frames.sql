-- Content identity -> enclosing real doFrame -> this app's SurfaceFrame ->
-- explicit FrameTimeline flow -> SurfaceFlinger DisplayFrame presentation.
-- Never substitute the app frame end (GPU/post completion) for presentation.
WITH RECURSIVE
draws AS (
  SELECT d.id, d.ts, d.dur, d.parent_id, t.upid,
         CAST(SUBSTR(d.name, 17) AS INTEGER) AS change_id,
         handler.ts AS handler_ts
  FROM slice d
  JOIN thread_track dt ON dt.id = d.track_id
  JOIN thread t ON t.utid = dt.utid
  JOIN process p ON p.upid = t.upid
  JOIN slice handler ON handler.name = 'FilmPreview:' || SUBSTR(d.name, 17)
  JOIN process_track ht ON ht.id = handler.track_id AND ht.upid = t.upid
  WHERE d.name GLOB 'FilmPreviewDraw:[0-9]*'
    AND p.name = 'io.github.haaaappyday.filmeditor.validation'
    AND d.dur >= 0 AND handler.dur >= 0
    AND d.ts >= handler.ts + handler.dur
    AND EXISTS (
      SELECT 1 FROM slice accepted
      JOIN thread_track at ON at.id = accepted.track_id
      JOIN thread ath ON ath.utid = at.utid
      WHERE accepted.name = 'FilmPreviewOutcome:' || SUBSTR(d.name, 17) || ':accepted'
        AND ath.upid = t.upid
        AND accepted.ts BETWEEN handler.ts AND handler.ts + handler.dur
    )
),
ancestors(draw_id, ancestor_id) AS (
  SELECT id, parent_id FROM draws WHERE parent_id IS NOT NULL
  UNION ALL
  SELECT a.draw_id, s.parent_id FROM ancestors a JOIN slice s ON s.id = a.ancestor_id
  WHERE s.parent_id IS NOT NULL
),
matching_frames AS (
  SELECT d.id AS draw_id,
         app.id AS app_frame_id, display.id AS display_frame_id,
         app.surface_frame_token, app.display_frame_token,
         display.ts + display.dur AS present_ns
  FROM draws d
  JOIN ancestors a ON a.draw_id = d.id
  JOIN slice frame ON frame.id = a.ancestor_id
  JOIN actual_frame_timeline_slice app
    ON app.surface_frame_token = CAST(SUBSTR(frame.name, INSTR(frame.name, ' ') + 1) AS INTEGER)
    AND app.upid = d.upid
  JOIN flow f ON f.slice_out = app.id
  JOIN actual_frame_timeline_slice display ON display.id = f.slice_in
    AND display.display_frame_token = app.display_frame_token
  JOIN process compositor ON compositor.upid = display.upid
  WHERE frame.name GLOB 'Choreographer#doFrame [0-9]*'
    AND app.surface_frame_token > 0 AND app.display_frame_token > 0
    AND app.layer_name GLOB '*io.github.haaaappyday.filmeditor.validation*MainActivity*'
    AND app.present_type IN ('On-time Present', 'Early Present', 'Late Present')
    AND display.present_type IN ('On-time Present', 'Early Present', 'Late Present')
    AND (display.surface_frame_token IS NULL OR display.surface_frame_token = 0)
    AND compositor.name IN ('/system/bin/surfaceflinger', 'surfaceflinger')
    AND app.dur >= 0 AND display.dur >= 0
    AND app.ts <= d.ts AND app.ts + app.dur >= d.ts + d.dur
    AND display.ts + display.dur >= d.ts + d.dur
    AND display.ts + display.dur >= app.ts + app.dur
),
unambiguous_frames AS (
  SELECT draw_id, MIN(app_frame_id) AS app_frame_id,
         MIN(display_frame_id) AS display_frame_id,
         MIN(surface_frame_token) AS surface_frame_token,
         MIN(display_frame_token) AS display_frame_token,
         MIN(present_ns) AS present_ns
  FROM matching_frames GROUP BY draw_id HAVING COUNT(*) = 1
)
SELECT d.change_id AS changeId, d.handler_ts AS handlerNs, d.ts AS drawNs,
       f.present_ns AS presentNs,
       f.surface_frame_token AS surfaceFrameToken, f.display_frame_token AS displayFrameToken,
       f.app_frame_id AS appFrameId, f.display_frame_id AS displayFrameId,
       CASE WHEN f.present_ns IS NULL THEN 0 ELSE 1 END AS flowVerified
FROM draws d LEFT JOIN unambiguous_frames f ON f.draw_id = d.id
ORDER BY d.change_id, d.ts;
