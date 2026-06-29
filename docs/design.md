# EtoileBridge Material Design 3 UI Guide

This document is the implementation guide for the Flutter UI refactor in stage
5.1.2.1. It is intentionally practical: every rule below should map to a code
choice in `lib/`.

## 1. Official Sources Read

Material Design 3:

- https://m3.material.io/
- https://m3.material.io/foundations
- https://m3.material.io/styles
- https://m3.material.io/components
- https://m3.material.io/components/cards
- https://m3.material.io/components/navigation-bar
- https://m3.material.io/components/navigation-rail
- https://m3.material.io/components/navigation-drawer
- https://m3.material.io/components/buttons
- https://m3.material.io/components/chips
- https://m3.material.io/components/dialogs
- https://m3.material.io/components/lists
- https://m3.material.io/components/progress-indicators
- https://m3.material.io/components/text-fields
- https://m3.material.io/foundations/adaptive-design
- https://m3.material.io/styles/motion

Flutter Material:

- https://docs.flutter.dev/ui/design/material
- https://docs.flutter.dev/ui/widgets/material
- https://docs.flutter.dev/release/breaking-changes/material-3-migration
- https://api.flutter.dev/flutter/material/material-library.html
- https://api.flutter.dev/flutter/material/ThemeData-class.html
- https://api.flutter.dev/flutter/material/ColorScheme-class.html
- https://api.flutter.dev/flutter/material/Card-class.html
- https://api.flutter.dev/flutter/material/NavigationRail-class.html
- https://api.flutter.dev/flutter/material/NavigationBar-class.html
- https://api.flutter.dev/flutter/material/NavigationDrawer-class.html
- https://api.flutter.dev/flutter/widgets/AnimatedSwitcher-class.html
- https://api.flutter.dev/flutter/widgets/AnimatedSize-class.html
- https://api.flutter.dev/flutter/material/PageTransitionsTheme-class.html

## 2. Material Design 3 Core Principles

- Use a coherent system, not isolated styled widgets: color, type, shape,
  elevation, layout, and motion should all point to the same hierarchy.
- Prefer standard components and their intended roles. A button should be a
  `FilledButton`, `OutlinedButton`, or `TextButton`, not a decorated container.
- Put the user's current task first. Diagnostics, logs, long paths, and advanced
  controls should support the task but not dominate the page.
- Surfaces should express hierarchy. A page background, a primary task card, an
  auxiliary card, and a nested list item cannot all use the same color, radius,
  and emphasis.

## 3. Color System

- Use `ThemeData(useMaterial3: true)` and a `ColorScheme` as the source of truth.
- EtoileBridge keeps a calm blue-green seed, but the UI must avoid being a wall
  of pale cyan. Use neutral `surface` colors for most cards and reserve
  `primaryContainer` / `secondaryContainer` for important status or selection.
- Suggested roles:
  - `surface`: page background and quiet regions.
  - `surfaceContainerLowest` / `surfaceContainerLow`: large page cards.
  - `surfaceContainer` / `surfaceContainerHigh`: nested list rows and preview
    frames.
  - `primary`: primary actions and active navigation.
  - `primaryContainer`: current task emphasis or success-adjacent highlight.
  - `secondaryContainer`: secondary contextual chips.
  - `tertiaryContainer`: preview or media accent where appropriate.
  - `errorContainer`: blocking error summaries.
- Success and warning are semantic additions, not replacements for the
  `ColorScheme`; keep them in a theme extension.

## 4. Typography

- Page title: `headlineSmall` or `titleLarge` on compact screens.
- Section title: `titleMedium`.
- Card subtitle / helper: `bodySmall`.
- Field label: `labelMedium`.
- Field value: `bodyMedium`.
- Use compact key-value rows for read-only metadata. Do not represent read-only
  values as disabled text fields.
- Long paths and raw JSON should use monospace only inside diagnostics.

## 5. Shape / Corner Radius

- Shape communicates component type and hierarchy.
- Radius tokens:
  - small: chips, tiny preview markers.
  - medium: list rows, compact nested cards.
  - large: normal cards and image previews.
  - extraLarge: hero/import cards and dialogs.
- Do not give every element a huge radius. A page full of 28px cards looks soft
  but not structured.

## 6. Elevation / Surface

- Prefer M3 tonal surfaces over strong shadows.
- Use elevated cards only for the current task, active media preview, or modal
  surfaces.
- Use outlined cards for optional actions, missing resources, diagnostics, and
  disabled future-stage features.
- Avoid nested filled cards inside filled cards; if nesting is needed, make the
  child surface smaller, lower-contrast, or outlined.

## 7. Layout / Adaptive Layout

Breakpoints:

- compact: `< 600`
- medium: `600 - 839`
- expanded: `840 - 1199`
- large: `1200 - 1599`
- extraLarge: `>= 1600`

Rules:

- compact: single column, `NavigationBar`, short header, save action visible.
- medium: `NavigationRail`, single column or narrow two-column only when safe.
- expanded and larger: `NavigationRail`, two-column task layout.
- extraLarge: constrain content width; do not let cards stretch across the
  entire monitor.
- Diagnostics and logs live in an auxiliary column or collapsed section.
- Avoid horizontal scrolling. Any width-derived calculation must clamp to safe
  positive values.

## 8. Motion / Animation

- Motion should clarify state changes, not decorate every frame.
- Use short durations: 120ms, 180ms, 240ms, 300ms.
- Use:
  - `AnimatedSwitcher` for status and task-state changes.
  - `AnimatedSize` for advanced / diagnostics / logs expansion.
  - `AnimatedContainer` for subtle card highlight or save-success emphasis.
  - Built-in Material ink/ripple for click feedback.
- Windows constraints:
  - Keep page switching on `Offstage + TickerMode`.
  - Do not restore the previous high-risk page-level `AnimatedOpacity`.
  - Keep accessibility downgrade strategy unless a later stage proves it safe.
- Respect `MediaQuery.disableAnimations` / reduced motion by shortening or
  removing non-essential transitions.

## 9. Navigation Components

- `NavigationRail`: desktop and tablet layouts where destinations are primary
  but should not consume a drawer-width side panel.
- Extended `NavigationRail`: only for large and extraLarge windows.
- `NavigationBar`: compact mobile layout with bottom navigation.
- `NavigationDrawer`: optional future choice if the app gets more destinations;
  not needed for the current three-page structure.

## 10. Cards

- Filled card: primary task or summary surface.
- Elevated card: active import / save task, modal-like emphasis, media preview.
- Outlined card: missing resource, diagnostics, disabled future-stage feature,
  or optional secondary block.
- Card content should usually be composed with `ListTile`, chips, buttons, and
  text fields rather than arbitrary rows of decorated boxes.

## 11. Buttons

- `FilledButton`: one primary action per task group, such as scan or save.
- `FilledButton.tonal`: secondary but still important actions, such as rescan
  or open location.
- `OutlinedButton`: file picking, optional actions, manual override.
- `TextButton`: low-emphasis actions inside dialogs or cards.
- `IconButton`: compact toolbar actions, but always with semantic labels where
  safe and useful.

## 12. Inputs and Selection Controls

- `TextFormField`: editable text/numeric metadata.
- `DropdownMenu`: one-of-many menu, especially difficulty/rating class if the
  options grow.
- `SegmentedButton`: small stable choices such as pack editor mode.
- `FilterChip` / `ChoiceChip`: resource state tags, optional filters, and status
  markers.
- `SwitchListTile` / `CheckboxListTile`: boolean preprocessing settings.

## 13. Lists, Expansion, Dialog, Feedback

- `ListTile`: compact resource, file, chart, and action rows.
- `ExpansionTile`: advanced metadata, AFF list, diagnostics, logs.
- `Dialog` / `AlertDialog`: settings, image preview, confirmation.
- `SnackBar`: lightweight feedback after jump, save, copy, or mock future-stage
  action.
- `MaterialBanner`: compact mobile warning such as Android single conversion not
  connected yet.
- `LinearProgressIndicator`: scan/save progress. Avoid fake cancel actions.

## 14. Flutter Component Mapping

- App shell: `Scaffold`, `AppBar`, `NavigationRail`, `NavigationBar`, `SafeArea`.
- Surfaces: `Card`, `Card.filled`, `Card.outlined`, `Material`.
- Forms: `TextFormField`, `InputDecorator`, `DropdownMenu`,
  `SegmentedButton`.
- Feedback: `AnimatedSwitcher`, `AnimatedSize`, `SnackBar`,
  `LinearProgressIndicator`, `MaterialBanner`.
- Lists: `ListView`, `ListTile`, `ExpansionTile`, `Divider`.

## 15. EtoileBridge Visual Direction

EtoileBridge should feel like a calm desktop/mobile tool for precise package
editing: quiet, modern, friendly, and structured. It should not look like a
debug dashboard. The visual identity is:

- warm off-white / neutral surface background;
- blue-green primary;
- gentle secondary accents;
- strong typography hierarchy;
- restrained rounded corners;
- media previews that feel like actual assets, not placeholders;
- diagnostics available, but not visually dominant.

## 16. Desktop Layout Rules

- Use `NavigationRail` on the left, not a custom wall-like sidebar.
- The page title lives in the content header.
- Main content is constrained and centered on very wide windows.
- Single song scanned state uses two columns:
  - primary column: import/status, summary, metadata, charts, save;
  - secondary column: media preview, resources, AFF, diagnostics/logs.
- Empty state should be a focused import view, not a page of empty cards.
- Save/export should remain visible early in the task flow after scan.

## 17. Mobile Layout Rules

- Use `NavigationBar`.
- Use one column.
- Keep top header compact.
- Show import first, status second, next/save action early.
- Advanced fields, diagnostics, and logs are collapsed by default.
- Bottom padding must avoid the navigation bar and gesture area.
- Android unavailable features must be shown as future-stage notices, never as
  fake success.

## 18. Motion Rules

- State cards change with `AnimatedSwitcher`.
- Expandable sections use `AnimatedSize`.
- Save success may use a short highlighted card transition.
- Media preview can fade in through `AnimatedSwitcher`; avoid platform-risky
  page-level opacity on Windows.
- Respect reduced motion.

## 19. Component Rules

- Use Material components for interaction and surfaces.
- Use custom widgets only as wrappers around Material components or for
  EtoileBridge-specific composition.
- Keep paths collapsed unless the user asks for details.
- Mark feature skeletons with clear future-stage text and disabled controls.
- Keep action logs and error logs available in diagnostics.

## 20. Forbidden UI Patterns

- `Container + BoxDecoration + GestureDetector` as a button.
- Custom bordered boxes as text fields.
- Giant empty cards before data exists.
- A full page of equally important filled cards.
- Pale cyan on every surface.
- All fields as editable text boxes.
- Default-expanded raw logs or JSON.
- Fake working buttons for Android scan/save before implementation.
- Page-level `AnimatedOpacity` on Windows.
- UI updates that remove safeAction, action log, or error log.

## 21. Current UI Gap List

- Empty single-song state still exposes too many later-stage panels.
- Scanned state needs a stronger summary and clearer save path.
- Resource previews need stronger media treatment and status chips.
- Diagnostics/logs need to be quieter and animated when expanded.
- Pack and character skeleton pages need to look like future-stage task pages,
  not mock/debug panels.
- Navigation is using M3 components but still needs softer hierarchy and content
  header treatment.
- Cards are still too uniform; primary, secondary, outlined, and disabled
  surfaces need clearer roles.

## 22. Stage 5.1.2.1 Refactor Plan

1. Tighten the design tokens and color/surface choices.
2. Add shared Material wrappers for section cards, animated sections, status
   banners, resource cards, and compact key-value rows.
3. Rework the app shell spacing/header so the rail feels standard and the
   content owns the page title.
4. Rebuild the single-song page around task states:
   - empty import state;
   - scanning state;
   - scanned editor state;
   - saved state.
5. Rework resource previews into media cards and resource list tiles.
6. Collapse diagnostics/logs by default with animated expansion.
7. Restyle pack and character skeletons with the same Material system.
8. Keep Windows scan/save and stability protections unchanged.
9. Run analyze, tests, Windows build, Windows run, Android run, and local
   single-song smoke checks.

## 23. Layout Reference Adaptation

Stage 5.2.1 adds a local layout study from
`E:\ArcpkgAPP\samples\界面排版设计示例`; see
`docs/layout_reference_analysis.md` for the image-by-image notes. The reference
images are used only for layout and density ideas. EtoileBridge must not copy
their non-MD3 colors, icon style, glossy effects, custom navigation treatment,
or decorative visual language.

### Borrowed layout methods

- Use a compact page header and put the real task content immediately below it.
- Split desktop editor screens into a primary task column and a narrower
  auxiliary preview/diagnostics column.
- Use top summary strips/cards after scan/save to show only the current state,
  warnings, selected chart, and save readiness.
- Keep repeated entries as compact rows; expand details on demand.
- Keep long paths, diagnostics, raw JSON, and logs collapsed.
- Use stable aspect ratios for media previews so images do not control page
  height.

### Non-MD3 styles that must not be copied

- No glossy gradients, glass panels, or decorative glow.
- No non-Material icon language.
- No custom shadow systems that fight Flutter Material surfaces.
- No large purple/blue slabs as a default background.
- No fake skeuomorphic placeholders.

### EtoileBridge final MD3 layout principles

- Page background uses neutral Material `surface`.
- Main task surfaces use filled or elevated cards sparingly.
- Auxiliary and diagnostic content uses outlined or low-emphasis cards.
- Repeated rows use `ListTile`, chips, compact thumbnails, and modest padding.
- Empty states render only the next meaningful task, not every possible section.

### Desktop page structure

- Navigation: standard `NavigationRail`.
- Content width: constrained and centered on wide screens.
- Layout: primary column flex is slightly larger than auxiliary column.
- Primary column contains import/status/edit/save.
- Auxiliary column contains media preview/resource list/diagnostics/logs.

### Mobile page structure

- Navigation: standard `NavigationBar`.
- Layout: one column, task sequence first, diagnostics last.
- The save or next primary action should appear before long auxiliary details.
- Compact two-up mini cards are allowed only when width is safe.

### Single song strategy

- Before scan: only page header, compact import panel, platform hint/current
  input. No empty overview, resources, logs, or metadata forms.
- After scan: summary first; chart selector at the top of the primary edit
  column; metadata and current chart editor appear once; save/export remains
  visible in the primary flow.
- Resources, AFF, diagnostics, and logs stay in the auxiliary column or
  collapsed sections.

### Pack editor strategy

- Before scan: mode selector, short mode actions, and quiet empty list/save
  state.
- After scan: compact pack summary, pack metadata editor, dense song list, save
  card, and an auxiliary preview/diagnostics column.
- Song entries remain compact by default; expanded content is per-entry and
  does not mount full editors for every row.

### Character skeleton strategy

- Keep it preview-led: result preview and position controls should look like
  the eventual editor.
- Input and metadata are compact task cards.
- Future-stage controls are clearly disabled or labelled; no mock success.

### Card and module density

- Compact card: repeated summaries, small task controls, list headers.
- Normal card: metadata editor, import controls, save/export.
- Spacious card: only the initial import/future-stage task when it is the
  whole screen focus.
- Nested surfaces should be lower contrast or outlined; avoid filled-card
  nesting.

### Button and primary action strategy

- Use `FilledButton` for the main action in a section.
- Use `FilledButton.tonal` for important secondary actions.
- Use `OutlinedButton` for choosing files, optional replacements, and
  non-destructive actions.
- Use `TextButton` for low-emphasis expansion, copy, and detail actions.
- Do not place every button in the card header; group actions near the content
  they affect.

### Stage 5.2.2 layout implementation rule

The verified reference directory is `E:\ArcpkgAPP\samples\界面排版设计示例`.
Stage 5.2.2 applies the reference structure more directly:

- desktop pages use narrow navigation, a compact top toolbar, and a constrained
  dashboard grid;
- single-song empty state shows import, compact info, conversion, preview, AFF,
  and export panels in their final positions instead of one oversized card;
- single-song scanned state keeps one summary, one chart selector, one metadata
  editor, one current-chart editor, and auxiliary resource/diagnostic panels;
- pack editor uses mode/input and pack metadata as primary tasks, a dense song
  list as the main work surface, and pack cover/selected song/logs as auxiliary
  panels;
- character skeleton follows the same task/preview split so the future real
  editor does not need another layout rewrite.

The implementation still uses Flutter Material 3 components and must not copy
the reference images' non-MD3 color, gradient, glass, icon, or shadow styles.
