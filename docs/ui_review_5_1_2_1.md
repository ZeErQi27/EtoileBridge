# Stage 5.1.2.1 UI Review

## 1. Main Problems in the Stage 5.1.2 UI

- The UI used Material widgets, but the page composition still resembled a debug
  panel: empty cards, equally weighted panels, and too much raw information.
- The single-song page showed overview, resources, AFF, appearance,
  preprocessing, save, diagnostics, and logs even before an input was selected.
- Card hierarchy was too flat. Most cards had the same surface treatment and
  visual weight.
- The app bar carried the current page title, while the content area lacked a
  strong page header.
- Resource previews looked functional but not like polished media previews.
- Pack and character pages still looked like mock demos rather than future-stage
  task pages.
- Motion was minimal. State transitions and expandable content felt abrupt.

## 2. How 5.1.2.1 Applies Material Design 3

- Added `docs/design.md` as a concrete Material 3 implementation guide.
- Tightened theme and surface hierarchy: page background, filled card, elevated
  card, outlined card, and nested media surfaces now have clearer roles.
- Moved page identity into content headers and restored the app bar to app
  identity.
- Rebuilt single-song layout around task state:
  - empty/import state;
  - scanning / failed state;
  - scanned editor state;
  - saved state.
- Reworked resources into media cards with status chips and list-tile actions.
- Reworked status feedback into animated Material-style notices.
- Pack and character pages now show explicit future-stage notices, with mock
  controls retained only for layout/state testing.

## 3. Material 3 Components Used

- `Scaffold`
- `AppBar`
- `NavigationRail`
- `NavigationBar`
- `Card`, `Card.filled`, `Card.outlined`
- `ListTile`
- `ExpansionTile`
- `FilledButton`, `FilledButton.tonal`, `OutlinedButton`, `TextButton`
- `IconButton`
- `TextField` / `TextFormField`
- `SegmentedButton`
- `FilterChip` / `Chip`
- `LinearProgressIndicator`
- `Dialog`
- `SnackBar`
- `AnimatedSwitcher`
- `AnimatedSize`

## 4. Custom UI Removed or Reduced

- Removed the page-level custom navigation wall in favor of `NavigationRail`.
- Replaced manually decorated warning/info boxes with `StatusNotice`, which is
  composed from Material surfaces and icons.
- Replaced resource gray frames with media `Card.filled` surfaces.
- Replaced pack level custom material rows with `Card.outlined`.
- Replaced future save buttons in mock pages with disabled Material actions.

## 5. Added Motion

- `AnimatedSwitcher` for single-song task-state changes.
- `AnimatedSwitcher` for status notice changes.
- `AnimatedSize` for pack list expansion and shared card content transitions.
- Existing Material ink/ripple is preserved through standard Material buttons,
  chips, list tiles, and cards.

## 6. Motion Deferred for Windows Stability

- Global Windows page-transition `AnimatedOpacity` remains disabled.
- Windows still uses `Offstage + TickerMode + IgnorePointer` for mounted page
  preservation.
- Global semantics restoration remains deferred because 5.0.3 identified it as
  risky for random Windows click stability.
- Hero/container-transform style image transitions were not added in this stage;
  image preview uses the existing stable Material dialog.

## 7. Desktop Points Requiring Human Visual Check

- Whether the two-column scanned single-song layout feels balanced on 1366,
  1920, and ultrawide displays.
- Whether the new app-level app bar plus content page header feels more native.
- Whether the right auxiliary column is visually quiet enough after real scan.

## 8. Mobile Points Requiring Human Visual Check

- Whether the empty/import state feels focused enough on narrow screens.
- Whether the bottom navigation and page header leave enough vertical space.
- Whether Android future-stage notices are clear without feeling like errors.

## 9. Remaining UI Debt

- Single-song chart editing still uses simple text fields; a later pass can move
  difficulty/rating class into dropdowns or segmented controls once the final
  data model is stable.
- Pack and character pages are still skeletons by design. Their final hierarchy
  should be revisited when real feature integration starts.
- Dark mode remains a future extension.
