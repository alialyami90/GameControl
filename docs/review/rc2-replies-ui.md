# rc2 – UI designer replies

- **A25 fixed.** `ActionEditor.kt`: the action-level "0 to 1 / -1 to 1" chips on a gamepad axis now only show when `axisOnly` is false. Sliders and joysticks (both call with `axisOnly = true`, Inspector.kt:138/148/150) no longer show them. The chips stay for an axis action anywhere else, where the action range still applies.
- **Slider range labelled.** `Inspector.kt`: the control-level chips under the slider's Axis section, which are now the only range control, get a "Range" label so they don't read as part of the axis picker. Joysticks show no range chips; their hint already says both axes run -1 to 1.
- Renders: `.impeccable/review/rc2/rc2_inspectors.png` (Throttle slider on the left, Bulldozer Drive / steer joystick on the right), plus the full-screen `rc2_slider.png` and `rc2_joystick.png`.
