## 2024-05-22 - Missing Password Visibility Toggle
**Learning:** Users often struggle with password entry on mobile devices due to small keyboards. A "Show Password" toggle is a critical micro-UX feature that prevents login frustration but is often overlooked in initial implementations.
**Action:** When auditing auth flows, always check for the presence of a password visibility toggle. Use `VisualTransformation` and `trailingIcon` in Jetpack Compose to implement this standard pattern.

## 2024-05-23 - Emoji vs. Semantic Icons
**Learning:** Using emojis for status indicators (like "📖" or "🎧") is visually compact but accessible poorly. Screen readers announce them literally ("Open book"), missing the context.
**Action:** Replace status emojis with `Icon` composables using explicit `contentDescription`s (e.g., "Ebook available"). This improves accessibility while maintaining the same visual footprint.
