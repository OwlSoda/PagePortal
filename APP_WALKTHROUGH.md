# UI Overhaul Walkthrough

I have completely revamped the PagePortal UI to focus on a cleaner, service-oriented experience.

## New Navigation Structure

The single "Library" screen has been replaced by a **Bottom Navigation Bar** with dedicated tabs:
- **Home**: A unified dashboard showing "Continue Reading" and "Recently Added" across all services.
- **Storyteller / ABS / Booklore**: Dedicated tabs for each service (currently visible for all, logic can be refined to hide unconnected ones).
- **Settings**: Easily accessible from the bottom bar.

## Settings & Reader Revamp
### Changes Made
- **Responsive Settings Menu**: Refactored `SettingsScreen` to use a responsive List/Detail layout that adapts to phones and tablets.
- **Reader Customization**:
    - Added granular controls for Font Family (Serif, Sans, Mono), Font Size, Line Height, Paragraph Spacing, Text Alignment, and Margins.
    - Implemented Theme selection (Light, Sepia, Dark, Black).
    - Added Brightness control with system/manual toggle.
    - Added Vertical Scroll vs Horizontal Pagination toggle.
- **Audio Player Enhancements**:
    - Implemented Sleep Timer (15, 30, 45, 60 min).
    - Added persistent Playback Speed.
    - Integrated Sleep Timer into the Reader audio controls.
- **Gesture Customization**:
    - Configurable tap zones (Left, Center, Right).
    - Actions: Previous Page, Next Page, Toggle Menu, None.

### Verification
1. **Settings Menu**:
    - Open Settings. Verify the dual-pane layout on tablets/landscape and single-pane on phones.
    - Navigate clearly organized categories (General, Reading, Audio, etc.).
2. **Reader Customization**:
    - Open a book.
    - Tap Settings icon.
    - Adjust Font, Spacing, Margins, Theme. Verify immediate visual feedback.
    - Toggle between Vertical Scroll and Pagination.
    - **Gestures**: Go to Global Settings -> Reading -> Gestures. Change Left Tap to "Next Page" (reverse logic). Go back to book and tap left. It should go forward. Change it back to "Previous Page".
3. **Audio Player**:
    - Open a book with audio (ReadAloud).
    - Tap the Sleep Timer icon (clock).
    - Select a duration. Wait for it to trigger (or implement short duration for test).
    - Change Playback Speed. Restart app and verify speed persists.

## Unified Home
The new Home screen (`UnifiedHomeScreen.kt`) serves as your starting point.
- **Continue Reading**: Quickly resume your active books.
- **Recently Added**: A horizontal list of the newest additions to your library.
- **Clean Interface**: Removed the crowded top bar buttons.

## Service Screens
Each service now has its own dedicated screen (`ServiceScreen.kt`) featuring a swipeable interface with four focused tabs:
1.  **Recent**: Grid view of recently added books.
2.  **Authors**: List of authors (clickable to filter).
3.  **Series**: List of series folders.
4.  **All**: Complete grid view of books from that service.

## Filter Fixes
- **Ebook vs ReadAloud**: The filters now work using "OR" logic (Union) rather than "AND". This means selecting "Ebook" and "ReadAloud" will show books that match *either* criteria, making it easier to see all your readable content.

## How to Verify
1.  Launch the app and observe the new Bottom Navigation Bar.
2.  Tap "Home" to see the dashboard.
3.  Tap a service tab (e.g., "Storyteller") and swipe left/right to switch between Recent, Authors, Series, and All views.
4.  Verify that the "Ebook" filter toggles visibility of ebooks correctly in the "All" view.
