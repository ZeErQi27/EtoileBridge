# ArcCreate Result Preview Assets

Stage: 5.3.2

## Purpose

Flutter character editor result preview uses a local, minimal copy of ArcCreate result-screen texture assets so the preview can match ArcCreate's actual result scene instead of using hand-drawn placeholder UI.

The copied assets are used only for the ArcCreate result-page preview in EtoileBridge Flutter. They are not test character images, sample arcpkg files, or user content.

## Source

Original source directory:

`E:\ArcpkgAPP\samples\ArcCreate-1.2.68\Assets\Textures\Result`

Flutter asset destination:

`E:\ArcpkgAPP\EtoileBridgeFlutter\assets\arccreate_result`

## Copied Assets

| Flutter asset | Original file | Purpose | ArcCreate / Android reference |
| --- | --- | --- | --- |
| `assets/arccreate_result/Background Arrow.png` | `Assets/Textures/Result/Background Arrow.png` | Back result decoration behind the partner layer. | `Result.unity` `BackgroundArrow`; old Android `backgroundArrowRect`. |
| `assets/arccreate_result/Clear Glow.png` | `Assets/Textures/Result/Clear Glow.png` | Clear-result glow behind foreground result UI. | `Result.unity` `ClearResult`; old Android `clearGlowRect`. |
| `assets/arccreate_result/Jacket Background.png` | `Assets/Textures/Result/Jacket Background.png` | Jacket frame/slot overlay in front of partner. | `Result.unity` `JacketFrame`; old Android `jacketRect`. |
| `assets/arccreate_result/Score Frame.png` | `Assets/Textures/Result/Score Frame.png` | Bottom score-frame overlay. | `Result.unity` `ScoreFrame/Background`; old Android `bottomScoreRect`. |
| `assets/arccreate_result/Judgement Table.png` | `Assets/Textures/Result/Judgement Table.png` | Judgement panel overlay. | `Result.unity` `JudgementFrame/JudgementTable`; old Android `resultPanelRect`. |
| `assets/arccreate_result/Judgement Table Highlight.png` | `Assets/Textures/Result/Judgement Table Highlight.png` | Judgement table highlight overlay. | `Result.unity` `JudgementTableHighlight`; old Android `judgementHighlightRect`. |
| `assets/arccreate_result/Play Retry Background.png` | `Assets/Textures/Result/Play Retry Background.png` | Play/retry bottom panel background. | `Result.unity` `PlayRetryTable`; old Android `playRetryRect`. |
| `assets/arccreate_result/Play Retry Frame.png` | `Assets/Textures/Result/Play Retry Frame.png` | Play/retry frame/highlight overlay. | `Result.unity` `Highlights`; old Android `playRetryHighlightRect`. |

## Not Copied

The full ArcCreate project, full `Assets` directory, `.meta` files, rank/grade subdirectories, score text, judgement text, and sample character images/arcpkg files are not copied.

## Why These Assets Are Needed

The result preview needs the real ArcCreate result-page visual layers to validate partner position, scale, and occlusion. The previous Flutter implementation used Canvas-drawn approximate panels, which was not sufficient for GUI validation.

## Runtime / Release Impact

These eight PNG files are declared in `pubspec.yaml` and will be included in Flutter builds. Their total size is small compared with the application and worker/runtime assets.

## License / Ownership Note

These images originate from the ArcCreate source tree. Before a public Flutter release that bundles these assets, confirm the final redistribution and attribution requirements for ArcCreate assets. If redistribution is not acceptable, replace them with user-provided ArcCreate assets or an opt-in external asset path.
