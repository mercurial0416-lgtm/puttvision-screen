# PuttVision V22

## Shareable performance reports
- Added one-tap PDF + CSV export from the session result screen.
- PDF summarizes score, make rate, start-line dispersion, face/path tendency, cup error, trend, putter ranking, and recent shot detail.
- CSV exposes the underlying shot metrics for spreadsheet/coaching analysis.
- Uses the existing private FileProvider/cache export path and Android share sheet.

## Custom green editor
- Added a persistent 5-zone green designer: BALL / 25% / 50% / 75% / CUP.
- Every zone controls SIDE and GRADE independently from -5% to +5% with smooth interpolation.
- The authored surface replaces preset/base slope while enabled instead of accidentally stacking on top.
- Rendering and GreenPhysics use the same GreenTerrain surface, including the height correction required when side slope changes along the putt.

## Putting audio
- Added asset-free procedural impact, subtle roll, cup-drop, lip-out, and near-cup feedback.
- Audio can be toggled from Product Setup and persists across restarts.

## TV thermal budget
- OpenGL TV rendering now uses WHEN_DIRTY with adaptive cadence: fast while the ball/camera moves and reduced refresh while idle/result is static.
- This leaves more thermal headroom for 120/240 fps camera capture on the same phone.

## Validation
- Consumer and developer unit/regression suites passed.
- Consumer and developer debug APK assembly passed.
- Real Galaxy S25 reference accuracy fixtures are still a separate physical validation requirement.
