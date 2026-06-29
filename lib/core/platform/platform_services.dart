import 'platform_cache_service.dart';
import 'platform_file_picker.dart';
import 'platform_open_location.dart';
import 'platform_paths.dart';
import 'platform_save_dialog.dart';
import 'platform_worker_bridge.dart';

class PlatformServices {
  PlatformServices({
    required this.filePicker,
    required this.saveDialog,
    required this.cache,
    required this.openLocation,
    required this.paths,
    required this.workerBridge,
  });

  factory PlatformServices.create() {
    final paths = RealPlatformPaths();
    return PlatformServices(
      filePicker: RealPlatformFilePicker(),
      saveDialog: RealPlatformSaveDialog(),
      cache: RealPlatformCacheService(paths),
      openLocation: RealPlatformOpenLocation(),
      paths: paths,
      workerBridge: createPlatformWorkerBridge(paths),
    );
  }

  factory PlatformServices.mock() {
    final paths = MockPlatformPaths();
    return PlatformServices(
      filePicker: MockPlatformFilePicker(),
      saveDialog: MockPlatformSaveDialog(),
      cache: MockPlatformCacheService(paths),
      openLocation: MockPlatformOpenLocation(),
      paths: paths,
      workerBridge: MockPlatformWorkerBridge(),
    );
  }

  final PlatformFilePicker filePicker;
  final PlatformSaveDialog saveDialog;
  final PlatformCacheService cache;
  final PlatformOpenLocation openLocation;
  final PlatformPaths paths;
  final PlatformWorkerBridge workerBridge;
}
