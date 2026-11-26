import 'package:flutter/material.dart';

abstract final class AppMotion {
  static const control = Duration(milliseconds: 140);
  static const reveal = Duration(milliseconds: 240);
  static const page = Duration(milliseconds: 300);
  static const overlay = Duration(milliseconds: 340);
  static const dismiss = Duration(milliseconds: 240);

  static const enter = Curves.easeOutCubic;
  static const travel = Curves.easeInOutCubicEmphasized;

  static Duration duration(BuildContext context, Duration duration) =>
      MediaQuery.disableAnimationsOf(context) ? Duration.zero : duration;
}

class AppPageViewport extends StatefulWidget {
  const AppPageViewport({required this.index, required this.pages, super.key});

  final int index;
  final List<Widget> pages;

  @override
  State<AppPageViewport> createState() => _AppPageViewportState();
}

class _AppPageViewportState extends State<AppPageViewport>
    with SingleTickerProviderStateMixin {
  late final _progress = AnimationController(
    vsync: this,
    duration: AppMotion.page,
    value: 1,
  );
  List<_PagePose> _from = const [];
  bool _reduceMotion = false;
  int _direction = 1;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _reduceMotion = MediaQuery.disableAnimationsOf(context);
    if (_reduceMotion) _progress.value = 1;
  }

  @override
  void didUpdateWidget(covariant AppPageViewport oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.index == widget.index) return;
    if (_reduceMotion) {
      _progress.value = 1;
      return;
    }

    final poses = List.generate(
      widget.pages.length,
      (index) => _poseFor(index, oldWidget.index),
    );
    _direction = widget.index > oldWidget.index ? 1 : -1;
    if (poses[widget.index].opacity == 0) {
      poses[widget.index] = _PagePose(0, 18.0 * _direction);
    }
    // Retarget from what is being painted, even if the last switch is mid-flight.
    _from = poses;
    _progress.forward(from: 0);
  }

  _PagePose _poseFor(int index, int activeIndex) {
    final active = index == activeIndex;
    final t = _progress.value;
    if (t == 1 || _from.length <= index) {
      return _PagePose(active ? 1 : 0, 0);
    }
    final from = _from[index];
    final fade = active
        ? const Interval(0.10, 1, curve: AppMotion.enter).transform(t)
        : const Interval(0, 0.55, curve: AppMotion.enter).transform(t);
    final targetOpacity = active ? 1 : 0;
    final targetOffset = active ? 0 : -12.0 * _direction;
    return _PagePose(
      from.opacity + (targetOpacity - from.opacity) * fade,
      from.dy + (targetOffset - from.dy) * AppMotion.travel.transform(t),
    );
  }

  @override
  void dispose() {
    _progress.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _progress,
      builder: (context, _) => ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            for (var index = 0; index < widget.pages.length; index++)
              if (index != widget.index) _slot(index),
            _slot(widget.index),
          ],
        ),
      ),
    );
  }

  Widget _slot(int index) {
    final active = index == widget.index;
    final pose = _poseFor(index, widget.index);
    return Offstage(
      key: ValueKey<int>(index),
      offstage: !active && pose.opacity == 0,
      child: TickerMode(
        enabled: active,
        child: ExcludeFocus(
          excluding: !active,
          child: ExcludeSemantics(
            excluding: !active,
            child: IgnorePointer(
              ignoring: !active,
              child: Transform.translate(
                offset: Offset(0, pose.dy),
                child: Opacity(
                  opacity: pose.opacity,
                  child: RepaintBoundary(child: widget.pages[index]),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _PagePose {
  const _PagePose(this.opacity, this.dy);

  final double opacity;
  final double dy;
}
