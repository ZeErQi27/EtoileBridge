# Stage 5.2.2 Layout Implementation Plan

This plan turns the local layout reference study into concrete Flutter changes.
The reference images in `E:\ArcpkgAPP\samples\界面排版设计示例` are used only for
layout structure, spacing, density, and task hierarchy. The implementation must
remain Material Design 3.

## Current UI vs Reference Gap

- The current pages still read as equal-weight card stacks. The reference
  screens use a clearer shell: narrow navigation, compact top identity/action
  bar, then a dashboard-like content grid.
- The current cards are too similar in size and weight. The reference screens
  mix compact summary cards, dense list rows, media panels, and focused task
  cards.
- Empty states currently hide too much behind one large import card. The
  references show empty dashboards with import, compact info, preview, selection,
  and export panels already in their final positions.
- Scanned states still repeat title/composer/input data. The references show
  one compact summary, then editors and previews with non-primary content
  collapsed.
- Pack editor needs the largest change: the song list must become the main
  working surface; pack cover, selected-song details, logs, and save status
  should be auxiliary panels.

## Layout Structures to Actually Adopt

### Left Navigation Width and Hierarchy

- Desktop keeps a narrow Material 3 `NavigationRail`.
- The rail should not become a wall-like custom panel; it only owns app
  destinations and settings access.
- Content owns the page title and current-page chip.

### Top Bar Organization

- Use an app shell top strip on desktop:
  - logo/title;
  - current page label chip;
  - settings action.
- Keep height modest. Do not create a large hero/header block.
- Mobile keeps a lightweight header and bottom `NavigationBar`.

### Main Content Grid

- Desktop content uses a fixed max width and a two-column grid:
  - primary column: current task and editing;
  - auxiliary column: media preview, resources, diagnostics, logs.
- Use primary:auxiliary ratio around `12:7` for pack and `11:7` for single and
  character.
- On compact/mobile, collapse to one column but keep the same task order.

### Card Size and Module Spacing

- Use compact cards for summary, status, path details, and logs.
- Use normal cards for metadata editors and save/export.
- Use large cards only for media/result preview when it is the focus.
- Repeated entries must be list-row density, not full editor cards.

### Button Placement

- Primary actions live close to the module they affect:
  - import buttons in import panel;
  - save button in save/export card;
  - expand/collapse in song-list header;
  - chart selector above current chart editor.
- Do not put all buttons in card headers.

### Preview Placement

- Single song: jacket/background/audio preview in auxiliary column.
- Pack: pack cover + selected song preview/details in auxiliary column.
- Character skeleton: ArcCreate result preview in primary area; character image
  and crop preview in auxiliary.

### Empty State Layout

- Single empty: show import dashboard plus compact placeholder info, preview,
  AFF, and export panels in final positions.
- Pack empty: mode selector and import controls in primary column; cover/list/save
  placeholders in auxiliary/final positions.
- Character empty: import, metadata, preview and disabled save in final positions.

### Imported/Scanned Layout

- Top compact summary card appears after scan.
- Metadata appears only once.
- Input/workspace/raw paths go to collapsed detail surfaces or compact rows.
- Logs and diagnostics are outlined/compact and visually lower priority.

## Widgets to Change

- `lib/shared/widgets/app_shell.dart`
  - tighten desktop top strip and rail proportions.
- `lib/shared/widgets/responsive_grid.dart`
  - keep `TaskColumns`, add optional compact/dashboard row helpers if needed.
- `lib/shared/cards/surface_card.dart`
  - keep compact/dense support; use it more aggressively.
- `lib/shared/widgets/md3_components.dart`
  - add small dashboard placeholder/summary helpers if repeated code grows.
- `lib/features/single_song/single_song_page.dart`
  - rebuild empty state as dashboard grid, not one import card.
  - scanned state keeps compact summary, task column and auxiliary column.
- `lib/features/pack_editor/pack_editor_page.dart`
  - make song list primary.
  - add selected song preview/details auxiliary panel.
  - keep save/export visible but lower than song list.
- `lib/features/character_editor/character_editor_page.dart`
  - keep preview-led skeleton; tighten sliders and image/crop surfaces.

## Old UI to Delete or Demote

- Delete single-song one-card empty hero.
- Delete equal-weight `ResponsiveGrid` usage for the three main pages.
- Demote source path/workspace/logs/raw diagnostics to compact or collapsed
  surfaces.
- Remove repeated overview facts that already appear in metadata editors.
- Remove large empty resource/log cards before data exists.

## Information to Merge

- `overview` and metadata:
  - overview only chips/facts;
  - metadata editor only editable fields.
- Input and workspace:
  - input summary in compact row;
  - raw path in details/logs.
- Pack cover and pack settings:
  - pack cover lives in auxiliary preview;
  - pack settings keeps text metadata only.

## Information to Fold

- diagnostics;
- raw paths;
- logs;
- advanced conversion settings;
- full chart/resource file details.

## Compact Card Targets

- single-song empty status/info placeholders;
- pack summary;
- pack save/export;
- pack logs;
- character save/logs;
- repeated chart/song row details.

## Modules Demoted to Auxiliary Area

- single resources/AFF/logs;
- pack cover/selected song details/logs;
- character image/icon crop/logs.

## Material Design 3 Boundary

- Use Flutter Material components for all controls and surfaces.
- Keep `ColorScheme`/theme tokens as source of truth.
- No copied gradients, non-MD3 icons, glass surfaces, custom shadow treatment,
  or decorative mock graphics from the references.
- Keep Windows accessibility downgrade and avoid page-level `AnimatedOpacity`.

## Platform Work in This Stage

- Android pack scan/save must be real if supported by the current native bridge
  and converter-core access.
- If old Android converter pack APIs are not reusable from this Flutter Android
  module without importing large build graph changes, implement the platform
  channel skeleton honestly and report the blocking native dependency work.
- Windows multi-arcpkg selection should return `List<String>` and feed the
  existing list-capable worker request.

## 5.2.2 Verification Note

The layout reference directory used for this implementation is
`E:\ArcpkgAPP\samples\界面排版设计示例`. The UI changes are based on its structural
patterns only: narrow navigation, compact toolbar, two-column dashboard grids,
primary task areas, auxiliary preview/export panels, and denser list rows. Its
non-Material color palettes, decorative shadows, icon style, and glass-like
effects are intentionally not copied.
