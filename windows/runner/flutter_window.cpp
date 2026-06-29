#include "flutter_window.h"

#include <algorithm>
#include <commdlg.h>
#include <cstdlib>
#include <mmsystem.h>
#include <optional>
#include <shlobj.h>
#include <shobjidl.h>
#include <string>
#include <vector>

#include "flutter/generated_plugin_registrant.h"
#include "utils.h"

namespace {

std::wstring Utf16FromUtf8(const std::string& utf8) {
  if (utf8.empty()) {
    return std::wstring();
  }
  int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, utf8.data(),
                                static_cast<int>(utf8.size()), nullptr, 0);
  if (size <= 0) {
    return std::wstring();
  }
  std::wstring result(size, L'\0');
  MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, utf8.data(),
                      static_cast<int>(utf8.size()), result.data(), size);
  return result;
}

std::vector<std::string> StringListArg(const flutter::EncodableValue* args,
                                       const char* key) {
  std::vector<std::string> values;
  if (!args) {
    return values;
  }
  const auto* map = std::get_if<flutter::EncodableMap>(args);
  if (!map) {
    return values;
  }
  auto it = map->find(flutter::EncodableValue(key));
  if (it == map->end()) {
    return values;
  }
  const auto* list = std::get_if<flutter::EncodableList>(&it->second);
  if (!list) {
    return values;
  }
  for (const auto& item : *list) {
    if (const auto* text = std::get_if<std::string>(&item)) {
      values.push_back(*text);
    }
  }
  return values;
}

std::string StringArg(const flutter::EncodableValue* args, const char* key) {
  if (!args) {
    return "";
  }
  const auto* map = std::get_if<flutter::EncodableMap>(args);
  if (!map) {
    return "";
  }
  auto it = map->find(flutter::EncodableValue(key));
  if (it == map->end()) {
    return "";
  }
  if (const auto* text = std::get_if<std::string>(&it->second)) {
    return *text;
  }
  return "";
}

int IntArg(const flutter::EncodableValue* args, const char* key) {
  if (!args) {
    return 0;
  }
  const auto* map = std::get_if<flutter::EncodableMap>(args);
  if (!map) {
    return 0;
  }
  auto it = map->find(flutter::EncodableValue(key));
  if (it == map->end()) {
    return 0;
  }
  if (const auto* value = std::get_if<int>(&it->second)) {
    return *value;
  }
  if (const auto* value64 = std::get_if<int64_t>(&it->second)) {
    return static_cast<int>(*value64);
  }
  return 0;
}

std::string MciErrorText(MCIERROR error) {
  wchar_t buffer[256] = L"";
  if (mciGetErrorStringW(error, buffer, static_cast<UINT>(_countof(buffer)))) {
    return Utf8FromUtf16(buffer);
  }
  return "MCI audio command failed";
}

MCIERROR MciSend(const std::wstring& command) {
  return mciSendStringW(command.c_str(), nullptr, 0, nullptr);
}

int MciStatusInt(const std::wstring& alias, const std::wstring& item) {
  wchar_t buffer[128] = L"";
  const std::wstring command = L"status " + alias + L" " + item;
  const MCIERROR error =
      mciSendStringW(command.c_str(), buffer, static_cast<UINT>(_countof(buffer)), nullptr);
  if (error != 0) {
    return 0;
  }
  return _wtoi(buffer);
}

bool MciIsPlaying(const std::wstring& alias) {
  wchar_t buffer[128] = L"";
  const std::wstring command = L"status " + alias + L" mode";
  const MCIERROR error =
      mciSendStringW(command.c_str(), buffer, static_cast<UINT>(_countof(buffer)), nullptr);
  if (error != 0) {
    return false;
  }
  return std::wstring(buffer) == L"playing";
}

flutter::EncodableValue AudioStatusMap(const std::wstring& alias) {
  flutter::EncodableMap map;
  map[flutter::EncodableValue("positionMs")] =
      flutter::EncodableValue(MciStatusInt(alias, L"position"));
  map[flutter::EncodableValue("durationMs")] =
      flutter::EncodableValue(MciStatusInt(alias, L"length"));
  map[flutter::EncodableValue("playing")] =
      flutter::EncodableValue(MciIsPlaying(alias));
  return flutter::EncodableValue(map);
}

std::wstring BuildFilter(const std::vector<std::string>& extensions) {
  std::wstring patterns;
  for (size_t i = 0; i < extensions.size(); i++) {
    if (i > 0) {
      patterns += L";";
    }
    patterns += L"*.";
    patterns += Utf16FromUtf8(extensions[i]);
  }
  if (patterns.empty()) {
    patterns = L"*.*";
  }
  std::wstring filter = L"Supported Files";
  filter.push_back(L'\0');
  filter += patterns;
  filter.push_back(L'\0');
  filter += L"All Files";
  filter.push_back(L'\0');
  filter += L"*.*";
  filter.push_back(L'\0');
  filter.push_back(L'\0');
  return filter;
}

flutter::EncodableValue NullablePath(const wchar_t* path) {
  if (!path || path[0] == L'\0') {
    return flutter::EncodableValue();
  }
  return flutter::EncodableValue(Utf8FromUtf16(path));
}

flutter::EncodableValue NullablePathList(const wchar_t* buffer) {
  flutter::EncodableList result;
  if (!buffer || buffer[0] == L'\0') {
    return flutter::EncodableValue(result);
  }

  const wchar_t* cursor = buffer;
  std::wstring directory(cursor);
  cursor += directory.size() + 1;
  if (*cursor == L'\0') {
    result.push_back(flutter::EncodableValue(Utf8FromUtf16(directory.c_str())));
    return flutter::EncodableValue(result);
  }

  while (*cursor != L'\0') {
    std::wstring file_name(cursor);
    std::wstring path = directory;
    if (!path.empty() && path.back() != L'\\' && path.back() != L'/') {
      path += L"\\";
    }
    path += file_name;
    result.push_back(flutter::EncodableValue(Utf8FromUtf16(path.c_str())));
    cursor += file_name.size() + 1;
  }
  return flutter::EncodableValue(result);
}

flutter::EncodableValue PickFolderWithFileDialog(HWND owner) {
  HRESULT coinit =
      CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE);
  const bool should_uninitialize = SUCCEEDED(coinit);
  if (FAILED(coinit) && coinit != RPC_E_CHANGED_MODE) {
    return flutter::EncodableValue();
  }

  IFileOpenDialog* dialog = nullptr;
  HRESULT hr = CoCreateInstance(CLSID_FileOpenDialog, nullptr,
                                CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&dialog));
  if (FAILED(hr) || dialog == nullptr) {
    if (should_uninitialize) {
      CoUninitialize();
    }
    return flutter::EncodableValue();
  }

  DWORD options = 0;
  if (SUCCEEDED(dialog->GetOptions(&options))) {
    dialog->SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM |
                       FOS_PATHMUSTEXIST | FOS_NOCHANGEDIR);
  }
  dialog->SetTitle(L"Select folder");

  flutter::EncodableValue result;
  hr = dialog->Show(owner);
  if (SUCCEEDED(hr)) {
    IShellItem* item = nullptr;
    hr = dialog->GetResult(&item);
    if (SUCCEEDED(hr) && item != nullptr) {
      PWSTR path = nullptr;
      hr = item->GetDisplayName(SIGDN_FILESYSPATH, &path);
      if (SUCCEEDED(hr)) {
        result = NullablePath(path);
        CoTaskMemFree(path);
      }
      item->Release();
    }
  }

  dialog->Release();
  if (should_uninitialize) {
    CoUninitialize();
  }
  return result;
}

}  // namespace

FlutterWindow::FlutterWindow(const flutter::DartProject& project)
    : project_(project) {}

FlutterWindow::~FlutterWindow() {}

bool FlutterWindow::OnCreate() {
  if (!Win32Window::OnCreate()) {
    return false;
  }

  RECT frame = GetClientArea();

  // The size here must match the window dimensions to avoid unnecessary surface
  // creation / destruction in the startup path.
  flutter_controller_ = std::make_unique<flutter::FlutterViewController>(
      frame.right - frame.left, frame.bottom - frame.top, project_);
  // Ensure that basic setup of the controller was successful.
  if (!flutter_controller_->engine() || !flutter_controller_->view()) {
    return false;
  }
  RegisterPlugins(flutter_controller_->engine());
  file_dialog_channel_ =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(),
          "com.zeerqi27.etoile_bridge/file_dialogs",
          &flutter::StandardMethodCodec::GetInstance());
  file_dialog_channel_->SetMethodCallHandler(
      [this](const flutter::MethodCall<flutter::EncodableValue>& call,
             std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>>
                 result) {
        if (call.method_name() == "pickFile") {
          wchar_t file_name[MAX_PATH] = L"";
          const auto filter = BuildFilter(
              StringListArg(call.arguments(), "extensions"));
          OPENFILENAMEW ofn = {};
          ofn.lStructSize = sizeof(ofn);
          ofn.hwndOwner = GetHandle();
          ofn.lpstrFile = file_name;
          ofn.nMaxFile = MAX_PATH;
          ofn.lpstrFilter = filter.c_str();
          ofn.nFilterIndex = 1;
          ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST |
                      OFN_NOCHANGEDIR | OFN_EXPLORER;
          if (GetOpenFileNameW(&ofn)) {
            result->Success(NullablePath(file_name));
          } else {
            result->Success(flutter::EncodableValue());
          }
          return;
        }
        if (call.method_name() == "pickFiles") {
          std::vector<wchar_t> file_names(65536, L'\0');
          const auto filter = BuildFilter(
              StringListArg(call.arguments(), "extensions"));
          OPENFILENAMEW ofn = {};
          ofn.lStructSize = sizeof(ofn);
          ofn.hwndOwner = GetHandle();
          ofn.lpstrFile = file_names.data();
          ofn.nMaxFile = static_cast<DWORD>(file_names.size());
          ofn.lpstrFilter = filter.c_str();
          ofn.nFilterIndex = 1;
          ofn.Flags = OFN_PATHMUSTEXIST | OFN_FILEMUSTEXIST |
                      OFN_NOCHANGEDIR | OFN_EXPLORER | OFN_ALLOWMULTISELECT;
          if (GetOpenFileNameW(&ofn)) {
            result->Success(NullablePathList(file_names.data()));
          } else {
            result->Success(flutter::EncodableList());
          }
          return;
        }
        if (call.method_name() == "pickFolder") {
          result->Success(PickFolderWithFileDialog(GetHandle()));
          return;
        }
        if (call.method_name() == "saveFile") {
          wchar_t file_name[MAX_PATH] = L"";
          const auto suggested =
              Utf16FromUtf8(StringArg(call.arguments(), "suggestedName"));
          wcsncpy_s(file_name, suggested.c_str(), MAX_PATH - 1);
          const auto initial_dir =
              Utf16FromUtf8(StringArg(call.arguments(), "initialDirectory"));
          const auto extension =
              Utf16FromUtf8(StringArg(call.arguments(), "extension"));
          const auto filter = BuildFilter({extension.empty() ? "arcpkg"
                                                            : StringArg(call.arguments(), "extension")});
          OPENFILENAMEW ofn = {};
          ofn.lStructSize = sizeof(ofn);
          ofn.hwndOwner = GetHandle();
          ofn.lpstrFile = file_name;
          ofn.nMaxFile = MAX_PATH;
          ofn.lpstrFilter = filter.c_str();
          ofn.lpstrDefExt = extension.empty() ? L"arcpkg" : extension.c_str();
          ofn.lpstrInitialDir =
              initial_dir.empty() ? nullptr : initial_dir.c_str();
          ofn.Flags = OFN_OVERWRITEPROMPT | OFN_PATHMUSTEXIST |
                      OFN_NOCHANGEDIR | OFN_EXPLORER;
          if (GetSaveFileNameW(&ofn)) {
            result->Success(NullablePath(file_name));
          } else {
            result->Success(flutter::EncodableValue());
          }
          return;
        }
        result->NotImplemented();
      });
  audio_preview_channel_ =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          flutter_controller_->engine()->messenger(),
          "com.zeerqi27.etoile_bridge/audio_preview",
          &flutter::StandardMethodCodec::GetInstance());
  audio_preview_channel_->SetMethodCallHandler(
      [this](const flutter::MethodCall<flutter::EncodableValue>& call,
             std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>>
                 result) {
        if (call.method_name() == "load") {
          const auto path = Utf16FromUtf8(StringArg(call.arguments(), "path"));
          CloseAudioPreview();
          if (path.empty()) {
            result->Error("audio_open_failed", "Empty audio path");
            return;
          }
          const std::wstring command =
              L"open \"" + path + L"\" alias " + audio_preview_alias_;
          MCIERROR error = MciSend(command);
          if (error != 0) {
            result->Error("audio_open_failed", MciErrorText(error));
            return;
          }
          audio_preview_open_ = true;
          MciSend(L"set " + audio_preview_alias_ +
                  L" time format milliseconds");
          result->Success(AudioStatusMap(audio_preview_alias_));
          return;
        }
        if (!audio_preview_open_) {
          result->Error("audio_not_loaded", "No audio loaded");
          return;
        }
        if (call.method_name() == "play") {
          MCIERROR error = MciSend(L"play " + audio_preview_alias_);
          if (error != 0) {
            result->Error("audio_play_failed", MciErrorText(error));
            return;
          }
          result->Success();
          return;
        }
        if (call.method_name() == "pause") {
          MCIERROR error = MciSend(L"pause " + audio_preview_alias_);
          if (error != 0) {
            result->Error("audio_pause_failed", MciErrorText(error));
            return;
          }
          result->Success();
          return;
        }
        if (call.method_name() == "seek") {
          const int position =
              std::max(0, IntArg(call.arguments(), "positionMs"));
          MCIERROR error = MciSend(L"seek " + audio_preview_alias_ + L" to " +
                                   std::to_wstring(position));
          if (error != 0) {
            result->Error("audio_seek_failed", MciErrorText(error));
            return;
          }
          result->Success();
          return;
        }
        if (call.method_name() == "position") {
          result->Success(AudioStatusMap(audio_preview_alias_));
          return;
        }
        if (call.method_name() == "close") {
          CloseAudioPreview();
          result->Success();
          return;
        }
        result->NotImplemented();
      });
  SetChildContent(flutter_controller_->view()->GetNativeWindow());

  flutter_controller_->engine()->SetNextFrameCallback([&]() {
    this->Show();
  });

  // Flutter can complete the first frame before the "show window" callback is
  // registered. The following call ensures a frame is pending to ensure the
  // window is shown. It is a no-op if the first frame hasn't completed yet.
  flutter_controller_->ForceRedraw();

  return true;
}

void FlutterWindow::OnDestroy() {
  CloseAudioPreview();
  if (flutter_controller_) {
    flutter_controller_ = nullptr;
  }

  Win32Window::OnDestroy();
}

void FlutterWindow::CloseAudioPreview() {
  if (!audio_preview_open_) {
    return;
  }
  MciSend(L"close " + audio_preview_alias_);
  audio_preview_open_ = false;
}

LRESULT
FlutterWindow::MessageHandler(HWND hwnd, UINT const message,
                              WPARAM const wparam,
                              LPARAM const lparam) noexcept {
  if (message == WM_GETOBJECT) {
    return 0;
  }

  // Give Flutter, including plugins, an opportunity to handle window messages.
  if (flutter_controller_) {
    std::optional<LRESULT> result =
        flutter_controller_->HandleTopLevelWindowProc(hwnd, message, wparam,
                                                      lparam);
    if (result) {
      return *result;
    }
  }

  switch (message) {
    case WM_FONTCHANGE:
      flutter_controller_->engine()->ReloadSystemFonts();
      break;
  }

  return Win32Window::MessageHandler(hwnd, message, wparam, lparam);
}
