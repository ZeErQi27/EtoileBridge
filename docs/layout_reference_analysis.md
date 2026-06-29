# Layout Reference Analysis for Stage 5.2.1

This document records the layout study for the local reference images in
`E:\ArcpkgAPP\samples\界面排版设计示例`. The images are not treated as visual style
targets. EtoileBridge keeps Flutter Material Design 3 components, colors,
motion, typography, and interaction patterns; only layout structure, spacing,
module organization, density, and hierarchy are adapted.

## Image Files Read

Single song:

- `单曲界面/桌面端1.png`
- `单曲界面/桌面端2.png`
- `单曲界面/桌面端3.png`
- `单曲界面/桌面端4.png`
- `单曲界面/移动端1.png`
- `单曲界面/移动端2.png`
- `单曲界面/移动端3.png`
- `单曲界面/移动端4.png`
- `单曲界面/移动端5.png`

Pack editor:

- `曲包界面/桌面端1.png`
- `曲包界面/桌面端2.png`
- `曲包界面/桌面端3.png`
- `曲包界面/桌面端4.png`
- `曲包界面/桌面端5.png`
- `曲包界面/移动端1.png`
- `曲包界面/移动端2.png`
- `曲包界面/移动端3.png`
- `曲包界面/移动端4.png`
- `曲包界面/移动端5.png`

Character editor:

- `搭档界面/桌面端1.png`
- `搭档界面/桌面端2.png`
- `搭档界面/桌面端3.png`
- `搭档界面/桌面端4.png`
- `搭档界面/移动端1.png`
- `搭档界面/移动端2.png`
- `搭档界面/一段段.png`

## Per-image Layout Notes

### Single Song Desktop 1

The empty/import state is split into a left task column and a right preview
column. The import target is compact and central inside its card; resource
preview, AFF selection, and export result are present but quiet. The useful
pattern is not the pale purple look, but the decision to keep empty diagnostics
out of the primary flow and to reserve the right side for visual/resource state.

### Single Song Desktop 2

After scanning, the left column holds current input, overview, and conversion
settings. The right column holds jacket/background/audio previews and AFF
selection. The most useful layout lesson is that preview cards use stable
aspect ratios, while chart/AFF choices are list rows rather than full forms.

### Single Song Desktop 3

This screen moves into a dense editor mode: detected charts are selected in a
compact list, the selected chart drives conversion settings, and advanced
settings are stacked in a right panel. It shows that a tool page can use three
visual zones: import/chart list, preview/selection, and settings/export. For
EtoileBridge, we adapt this as a primary edit column plus a narrower auxiliary
column.

### Single Song Desktop 4

The finished state uses a full-width success status strip, then compact cards
for import summary, preview, metadata, and export results. This is a good model
for preserving save result context without leaving the user in a raw log panel.
The success message should be a status surface; output files should be list
rows with actions.

### Single Song Mobile 1-5

The mobile references keep a short header, then task cards in a clear sequence:
import, status, compact overview, resource preview, chart selector, conversion
settings, and a prominent bottom action. Useful patterns: large primary actions
can be placed early, while long paths and diagnostics are hidden. On compact
screens, paired mini-cards can sit in a two-column grid only when width allows.

### Pack Desktop 1

The empty pack screen is split between import/settings on the left and empty
song list/save/logs on the right. The important point is that the mode selector
is the first control and does not compete with pack metadata. Empty song list
and save states are quiet cards, not full mock lists.

### Pack Desktop 2

This screen emphasizes a compact pack summary and a right-side song list. It
keeps pack configuration in a moderate card and uses the list as the primary
working area. EtoileBridge should use this density once scan succeeds.

### Pack Desktop 3

The mature pack-editor layout is the strongest reference: left side has import
and pack metadata, center has dense song rows with filters/chips/actions, and a
right details rail shows selected song metadata. This avoids making every song a
giant card. EtoileBridge should adapt this by making song rows compact and
showing expanded details only when needed.

### Pack Desktop 4-5

These continue the same hierarchy: mode/input, summary, dense song list, and
details/actions. The key spacing rule is that list rows are allowed to be much
shorter than cards; card padding stays modest and repeated content goes into
chips or secondary text.

### Pack Mobile 1-5

The mobile pack references use a prominent mode selector, filter chips, a search
row, compact song cards, and a bottom/detail sheet for the selected song. This
translates to EtoileBridge as a single-column task flow with compact rows and
collapsed details, not as a vertical stack of full song editors.

### Character Desktop 1-4

The strongest character layout puts input and metadata on the left, a large
ArcCreate result preview on the right, and save/logs beneath. Parameter sliders
are directly under the preview, so the user sees the effect of changes. The
plain character image preview stays secondary. EtoileBridge's skeleton should
use the same preview-first organization even before real character conversion
is connected.

### Character Mobile 1-2

The mobile character references show a simple task sequence: import, import
status, metadata, character image, result preview, save. The result preview is a
compact panel with controls beside or below it. Skeleton controls should be
disabled or future-stage, but still laid out like the final workflow.

### Character "一段段"

This image reinforces section-by-section grouping. It is useful for mobile
progressive disclosure: one task per card, no giant all-in-one card, and
diagnostics/logs below the save area.

## Spacing and Breathing Rules

- Page-level gaps are larger than inside-card gaps.
- Repeated list items use compact padding and lower-radius rows.
- Primary task cards can be spacious, but repeated song/chart rows must be
  dense.
- Empty state cards should be focused and shallow; do not render empty overview,
  metadata, resource, log, and diagnostics cards.
- Paths, raw JSON, and logs should live in collapsed sections.
- Media previews need stable aspect ratios so they do not dictate the whole
  page height.

## Card Size and Density Rules

- Use a compact summary strip/card above the main editor after scan.
- Use one primary editor column and one auxiliary preview/diagnostics column on
  desktop.
- Keep pack song rows around list-row density; expanded details are optional and
  should not mount as a full form for every song.
- On mobile, show only the next useful task and collapse advanced sections.

## Navigation Rules

- Desktop keeps a narrow Material 3 `NavigationRail`.
- Mobile keeps Material 3 `NavigationBar`.
- Page title stays in the content area, not as a heavy sidebar banner.
- Settings/history actions should be compact actions, not large cards.

## Main vs Auxiliary Organization

- Single song: primary = import/status/chart selector/metadata/current chart/save;
  auxiliary = jacket/background/audio/resources/AFF/diagnostics/logs.
- Pack editor: primary = mode/input/pack metadata/song list/save; auxiliary =
  pack cover/selected song preview/diagnostics/logs.
- Character editor: primary = input/metadata/save; auxiliary = result preview,
  character image, crop/position controls.

## Suitable Adaptations for EtoileBridge

- Add a reusable two-column task layout with a wider primary column and narrower
  auxiliary column.
- Add compact section cards with smaller padding for list-like content.
- Add summary strips that use chips and short facts instead of repeated forms.
- Make empty states minimal.
- Keep diagnostics/logs collapsed or outlined.

## Styles Not to Copy

- Do not copy non-MD3 gradients, glossy decoration, glass effects, icon style,
  custom navigation visuals, or color palette.
- Do not copy large decorative placeholders when real Material 3 empty states
  and tonal cards are sufficient.
- Do not use non-standard shadow or border treatment.

## Current EtoileBridge Problems

- Single-song empty state still feels too hero-like for a utility task.
- Scanned single-song state repeats overview and editable metadata.
- Pack page still distributes cards evenly instead of prioritizing the song list.
- Character skeleton is a mock grid rather than a preview-led workflow.
- `ResponsiveGrid` alternates cards by index, which creates accidental hierarchy.
- Card padding/radius are too uniform; repeated rows need lower density.

## Stage 5.2.1 Implementation Plan

1. Add layout helpers for primary/auxiliary task columns and compact surface
   cards.
2. Reduce empty-state card padding and make import panels focused.
3. Reorder single-song scanned state into compact summary, primary edit column,
   auxiliary preview column.
4. Reorder pack editor into mode/input + pack settings + song list as primary,
   cover/save/logs as auxiliary.
5. Reorder character skeleton around a large result preview and compact
   controls.
6. Preserve all worker bridge, UTF-8, safeAction, action log, error log, Windows
   accessibility downgrade, and Offstage/TickerMode page behavior.

## 5.2.2 Verified Reference File List

The directory was rechecked in stage 5.2.2. The actual files read are:

- `单曲界面/桌面端1.png`
- `单曲界面/桌面端2.png`
- `单曲界面/桌面端3.png`
- `单曲界面/桌面端4.png`
- `单曲界面/移动端1.png`
- `单曲界面/移动端2.png`
- `单曲界面/移动端3.png`
- `单曲界面/移动端4.png`
- `单曲界面/移动端5.png`
- `曲包界面/桌面端1.png`
- `曲包界面/桌面端2.png`
- `曲包界面/桌面端3.png`
- `曲包界面/桌面端4.png`
- `曲包界面/桌面端5.png`
- `曲包界面/移动端1.png`
- `曲包界面/移动端2.png`
- `曲包界面/移动端3.png`
- `曲包界面/移动端4.png`
- `曲包界面/移动端5.png`
- `搭档界面/一段段.png`
- `搭档界面/桌面端1.png`
- `搭档界面/桌面端2.png`
- `搭档界面/桌面端3.png`
- `搭档界面/桌面端4.png`
- `搭档界面/移动端1.png`
- `搭档界面/移动端2.png`
