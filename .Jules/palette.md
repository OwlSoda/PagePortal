## 2024-05-22 - Missing Password Visibility Toggle
**Learning:** Users often struggle with password entry on mobile devices due to small keyboards. A "Show Password" toggle is a critical micro-UX feature that prevents login frustration but is often overlooked in initial implementations.
**Action:** When auditing auth flows, always check for the presence of a password visibility toggle. Use `VisualTransformation` and `trailingIcon` in Jetpack Compose to implement this standard pattern.

## 2024-05-23 - Emoji vs. Semantic Icons
**Learning:** Using emojis for status indicators (like "📖" or "🎧") is visually compact but accessible poorly. Screen readers announce them literally ("Open book"), missing the context.
**Action:** Replace status emojis with `Icon` composables using explicit `contentDescription`s (e.g., "Ebook available"). This improves accessibility while maintaining the same visual footprint.

## 2024-05-24 - Slider Precision Controls
**Learning:** Touch sliders are often imprecise, making it difficult for users to select specific values (like an exact font size). Adding incremental +/- buttons alongside the slider significantly improves usability and accessibility for fine-tuning.
**Action:** When using `Slider` for precise adjustments, always consider wrapping it in a `Row` with decrement/increment buttons. Ensure these buttons have proper `contentDescription` and `enabled` states based on the slider's range to prevent crashes or invalid states.

## 2024-05-25 - Preventing Slider Drift
**Learning:** Sliders bound to floating point values tend to accumulate small drift issues when interacted with via discrete tap increments or continuous drags. Without bounding and rounding constraints, precision numbers may become visibly mangled or crash configurations.
**Action:** Sliders bound to floats (e.g. brightness, playback speed) must be bounded with `.coerceAtLeast` and `.coerceAtMost`, and when discrete step values are needed, value transformations should use robust logic like `Math.round((current - step) * scale) / scale` to avoid floating-point imprecision.

## 2026-04-01 - Keyboard ImeAction for Form Navigation
**Learning:** Users filling out forms on mobile expect to use the keyboard's bottom-right action button to navigate to the next field or submit the form. Without `ImeAction.Next` and `ImeAction.Done` configured on text fields, the keyboard defaults to creating new lines or simply hiding, which disrupts flow.
**Action:** When implementing sequential forms in Compose (like login or settings), add `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)` to intermediate fields, and `ImeAction.Done` with a corresponding `KeyboardActions(onDone = { ... })` to the final field to trigger submission.
