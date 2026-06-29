import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const _systemChannel = MethodChannel('com.zeerqi27.etoile_bridge/system');

Future<void> openExternalUrl(String url) async {
  final uri = Uri.tryParse(url);
  if (uri == null || !uri.hasScheme) {
    throw ArgumentError.value(url, 'url', 'Invalid URL');
  }
  if (kIsWeb) {
    throw UnsupportedError('External URL opening is not supported on web.');
  }
  if (Platform.isAndroid) {
    await _systemChannel.invokeMethod<void>('openUrl', {'url': url});
    return;
  }
  if (Platform.isWindows) {
    await Process.start('rundll32', ['url.dll,FileProtocolHandler', url]);
    return;
  }
  if (Platform.isMacOS) {
    await Process.start('open', [url]);
    return;
  }
  if (Platform.isLinux) {
    await Process.start('xdg-open', [url]);
    return;
  }
  throw UnsupportedError('External URL opening is not supported here.');
}
