import 'dart:convert';
import 'dart:io';

import '../../app/app_version.dart';

const githubLatestReleaseApi =
    'https://api.github.com/repos/ZeErQi27/EtoileBridge/releases/latest';
const githubReleasesPage = 'https://github.com/ZeErQi27/EtoileBridge/releases';

enum UpdateCheckStatus {
  updateAvailable,
  upToDate,
  unknown,
  rateLimited,
  networkError,
}

class UpdateCheckResult {
  const UpdateCheckResult({
    required this.status,
    required this.currentVersion,
    this.latestVersion,
    this.releaseUrl,
    this.error,
  });

  final UpdateCheckStatus status;
  final String currentVersion;
  final String? latestVersion;
  final String? releaseUrl;
  final String? error;

  bool get canOpenReleasePage =>
      releaseUrl != null || status == UpdateCheckStatus.rateLimited;
}

class GitHubUpdateChecker {
  const GitHubUpdateChecker();

  Future<UpdateCheckResult> check({
    String currentVersion = appVersionName,
  }) async {
    final client = HttpClient();
    try {
      final request = await client.getUrl(Uri.parse(githubLatestReleaseApi));
      request.headers.set(
        HttpHeaders.acceptHeader,
        'application/vnd.github+json',
      );
      request.headers.set(HttpHeaders.userAgentHeader, 'EtoileBridge Flutter');
      final response = await request.close();
      final body = await response.transform(utf8.decoder).join();
      if (response.statusCode == 403 || response.statusCode == 429) {
        return UpdateCheckResult(
          status: UpdateCheckStatus.rateLimited,
          currentVersion: currentVersion,
          releaseUrl: githubReleasesPage,
          error: body,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        return UpdateCheckResult(
          status: UpdateCheckStatus.networkError,
          currentVersion: currentVersion,
          releaseUrl: githubReleasesPage,
          error: 'HTTP ${response.statusCode}: $body',
        );
      }
      final decoded = jsonDecode(body);
      if (decoded is! Map) {
        return UpdateCheckResult(
          status: UpdateCheckStatus.unknown,
          currentVersion: currentVersion,
          releaseUrl: githubReleasesPage,
          error: 'Unexpected GitHub response.',
        );
      }
      final tag = decoded['tag_name']?.toString();
      final htmlUrl = decoded['html_url']?.toString().trim().isNotEmpty == true
          ? decoded['html_url'].toString()
          : githubReleasesPage;
      if (tag == null || tag.trim().isEmpty) {
        return UpdateCheckResult(
          status: UpdateCheckStatus.unknown,
          currentVersion: currentVersion,
          releaseUrl: htmlUrl,
          error: 'Latest release tag is missing.',
        );
      }
      final comparison = compareVersions(tag, currentVersion);
      if (comparison == null) {
        return UpdateCheckResult(
          status: UpdateCheckStatus.unknown,
          currentVersion: currentVersion,
          latestVersion: tag,
          releaseUrl: htmlUrl,
          error: 'Version could not be compared reliably.',
        );
      }
      return UpdateCheckResult(
        status: comparison > 0
            ? UpdateCheckStatus.updateAvailable
            : UpdateCheckStatus.upToDate,
        currentVersion: currentVersion,
        latestVersion: tag,
        releaseUrl: htmlUrl,
      );
    } on SocketException catch (error) {
      return UpdateCheckResult(
        status: UpdateCheckStatus.networkError,
        currentVersion: currentVersion,
        releaseUrl: githubReleasesPage,
        error: error.message,
      );
    } on FormatException catch (error) {
      return UpdateCheckResult(
        status: UpdateCheckStatus.unknown,
        currentVersion: currentVersion,
        releaseUrl: githubReleasesPage,
        error: error.message,
      );
    } catch (error) {
      return UpdateCheckResult(
        status: UpdateCheckStatus.networkError,
        currentVersion: currentVersion,
        releaseUrl: githubReleasesPage,
        error: error.toString(),
      );
    } finally {
      client.close(force: true);
    }
  }
}

int? compareVersions(String latest, String current) {
  final left = _ParsedVersion.parse(latest);
  final right = _ParsedVersion.parse(current);
  if (left == null || right == null) return null;
  final maxLength = left.parts.length > right.parts.length
      ? left.parts.length
      : right.parts.length;
  for (var i = 0; i < maxLength; i++) {
    final l = i < left.parts.length ? left.parts[i] : 0;
    final r = i < right.parts.length ? right.parts[i] : 0;
    if (l != r) return l.compareTo(r);
  }
  if (left.preRelease == right.preRelease) return 0;
  if (!left.preRelease && right.preRelease) return 1;
  if (left.preRelease && !right.preRelease) return -1;
  return 0;
}

class _ParsedVersion {
  const _ParsedVersion({required this.parts, required this.preRelease});

  final List<int> parts;
  final bool preRelease;

  static _ParsedVersion? parse(String raw) {
    var value = raw.trim();
    if (value.startsWith('v') || value.startsWith('V')) {
      value = value.substring(1);
    }
    value = value.split('+').first;
    final preRelease = value.contains('-');
    value = value.split('-').first;
    final parts = value
        .split('.')
        .where((part) => part.trim().isNotEmpty)
        .map((part) => int.tryParse(part.trim()))
        .toList();
    if (parts.isEmpty || parts.any((part) => part == null)) return null;
    return _ParsedVersion(
      parts: parts.whereType<int>().toList(growable: false),
      preRelease: preRelease,
    );
  }
}
