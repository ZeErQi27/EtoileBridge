#include "win32_window.h"

#include <dwmapi.h>
#include <flutter_windows.h>

#include "resource.h"

namespace {

/// Window attribute that enables dark mode window decorations.
///
/// Redefined in case the developer's machine has a Windows SDK older than
/// version 10.0.22000.0.
/// See: https://docs.microsoft.com/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute
#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif

#ifndef DWMWA_SYSTEMBACKDROP_TYPE
#define DWMWA_SYSTEMBACKDROP_TYPE 38
#endif

#ifndef DWMWA_BORDER_COLOR
#define DWMWA_BORDER_COLOR 34
#endif

#ifndef DWMWA_CAPTION_COLOR
#define DWMWA_CAPTION_COLOR 35
#endif

#ifndef DWMWA_TEXT_COLOR
#define DWMWA_TEXT_COLOR 36
#endif

constexpr const wchar_t kWindowClassName[] = L"FLUTTER_RUNNER_WIN32_WINDOW";

/// Registry key for app theme preference.
///
/// A value of 0 indicates apps should use dark mode. A non-zero or missing
/// value indicates apps should use light mode.
constexpr const wchar_t kGetPreferredBrightnessRegKey[] =
  L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
constexpr const wchar_t kGetPreferredBrightnessRegValue[] = L"AppsUseLightTheme";

// The number of Win32Window objects that currently exist.
static int g_active_window_count = 0;

constexpr int kMinWindowWidth = 900;
constexpr int kMinWindowHeight = 600;

using EnableNonClientDpiScaling = BOOL __stdcall(HWND hwnd);

enum DwmSystemBackdropType {
  kDwmSystemBackdropAuto = 0,
  kDwmSystemBackdropNone = 1,
  kDwmSystemBackdropMainWindow = 2,
  kDwmSystemBackdropTransientWindow = 3,
  kDwmSystemBackdropTabbedWindow = 4,
};

enum AccentState {
  kAccentDisabled = 0,
  kAccentEnableGradient = 1,
  kAccentEnableTransparentGradient = 2,
  kAccentEnableBlurBehind = 3,
  kAccentEnableAcrylicBlurBehind = 4,
};

struct AccentPolicy {
  int accent_state;
  int accent_flags;
  int gradient_color;
  int animation_id;
};

struct WindowCompositionAttributeData {
  int attribute;
  void* data;
  size_t size_of_data;
};

using SetWindowCompositionAttribute = BOOL(WINAPI*)(
    HWND hwnd,
    WindowCompositionAttributeData* data);

constexpr int kWindowCompositionAttributeAccentPolicy = 19;

// Scale helper to convert logical scaler values to physical using passed in
// scale factor
int Scale(int source, double scale_factor) {
  return static_cast<int>(source * scale_factor);
}

// Dynamically loads the |EnableNonClientDpiScaling| from the User32 module.
// This API is only needed for PerMonitor V1 awareness mode.
void EnableFullDpiSupportIfAvailable(HWND hwnd) {
  HMODULE user32_module = LoadLibraryA("User32.dll");
  if (!user32_module) {
    return;
  }
  auto enable_non_client_dpi_scaling =
      reinterpret_cast<EnableNonClientDpiScaling*>(
          GetProcAddress(user32_module, "EnableNonClientDpiScaling"));
  if (enable_non_client_dpi_scaling != nullptr) {
    enable_non_client_dpi_scaling(hwnd);
  }
  FreeLibrary(user32_module);
}

bool EnableAcrylicBackdrop(HWND window) {
  if (!window) {
    return false;
  }

  HMODULE user32_module = GetModuleHandleW(L"user32.dll");
  if (!user32_module) {
    return false;
  }

  auto set_window_composition_attribute =
      reinterpret_cast<SetWindowCompositionAttribute>(GetProcAddress(
          user32_module, "SetWindowCompositionAttribute"));
  if (!set_window_composition_attribute) {
    return false;
  }

  // Windows Fluent acrylic. The color is ABGR: alpha + pale Etoile blue tint.
  // This is intentionally not a layered/transparent-window hack; it asks DWM
  // for a blurred backdrop and keeps Flutter content readable above it.
  AccentPolicy accent = {
      kAccentEnableAcrylicBlurBehind,
      0,
      static_cast<int>(0x98FFF7EE),
      0,
  };
  WindowCompositionAttributeData data = {
      kWindowCompositionAttributeAccentPolicy,
      &accent,
      sizeof(accent),
  };
  return set_window_composition_attribute(window, &data) == TRUE;
}

bool EnableMicaSystemBackdrop(HWND window) {
  if (!window) {
    return false;
  }

  // Windows 11 Mica system backdrop. Unsupported Windows versions return a
  // failing HRESULT and keep Flutter's normal light fallback background.
  const int backdrop = kDwmSystemBackdropMainWindow;
  const HRESULT result = DwmSetWindowAttribute(
      window, DWMWA_SYSTEMBACKDROP_TYPE, &backdrop, sizeof(backdrop));
  return SUCCEEDED(result);
}

void EnableFluentBackdrop(HWND window) {
  if (!window) {
    return;
  }

  const bool acrylic_enabled = EnableAcrylicBackdrop(window);
  const bool mica_enabled = acrylic_enabled ? false : EnableMicaSystemBackdrop(window);
  static bool logged_backdrop_result = false;
  if (!logged_backdrop_result) {
    logged_backdrop_result = true;
    if (acrylic_enabled) {
      OutputDebugStringW(L"EtoileBridge: Windows Acrylic backdrop enabled.\n");
    } else if (mica_enabled) {
      OutputDebugStringW(
          L"EtoileBridge: Acrylic unavailable; DWM Mica backdrop enabled.\n");
    } else {
      OutputDebugStringW(
          L"EtoileBridge: Fluent backdrop unavailable; using light fallback.\n");
    }
  }
}

void ApplyCaptionColors(HWND window, bool dark) {
  if (!window) {
    return;
  }

  // Keep the native Windows caption buttons and resize frame, but tint the
  // non-client area toward EtoileBridge's Acrylic surface. This avoids the
  // hard default-gray titlebar cut while preserving system window behavior.
  const COLORREF caption_color =
      dark ? RGB(22, 32, 42) : RGB(242, 250, 254);
  const COLORREF border_color =
      dark ? RGB(48, 80, 96) : RGB(200, 235, 248);
  const COLORREF text_color =
      dark ? RGB(238, 248, 252) : RGB(16, 32, 48);

  DwmSetWindowAttribute(window, DWMWA_CAPTION_COLOR, &caption_color,
                        sizeof(caption_color));
  DwmSetWindowAttribute(window, DWMWA_BORDER_COLOR, &border_color,
                        sizeof(border_color));
  DwmSetWindowAttribute(window, DWMWA_TEXT_COLOR, &text_color,
                        sizeof(text_color));
}

HICON LoadSharedAppIcon(int width, int height) {
  return reinterpret_cast<HICON>(LoadImage(
      GetModuleHandle(nullptr), MAKEINTRESOURCE(IDI_APP_ICON), IMAGE_ICON,
      width, height, LR_DEFAULTCOLOR | LR_SHARED));
}

void ApplyWindowIcons(HWND window) {
  if (!window) {
    return;
  }

  HICON small_icon = LoadSharedAppIcon(GetSystemMetrics(SM_CXSMICON),
                                       GetSystemMetrics(SM_CYSMICON));
  HICON big_icon =
      LoadSharedAppIcon(GetSystemMetrics(SM_CXICON), GetSystemMetrics(SM_CYICON));

  // Make the EtoileBridge icon explicit for the title bar, taskbar and Alt-Tab.
  // Class registration alone can leave a stale/default icon after runner
  // resource changes, especially during iterative Flutter desktop builds.
  if (small_icon) {
    SendMessage(window, WM_SETICON, ICON_SMALL,
                reinterpret_cast<LPARAM>(small_icon));
  }
  if (big_icon) {
    SendMessage(window, WM_SETICON, ICON_BIG, reinterpret_cast<LPARAM>(big_icon));
  }
}

}  // namespace

// Manages the Win32Window's window class registration.
class WindowClassRegistrar {
 public:
  ~WindowClassRegistrar() = default;

  // Returns the singleton registrar instance.
  static WindowClassRegistrar* GetInstance() {
    if (!instance_) {
      instance_ = new WindowClassRegistrar();
    }
    return instance_;
  }

  // Returns the name of the window class, registering the class if it hasn't
  // previously been registered.
  const wchar_t* GetWindowClass();

  // Unregisters the window class. Should only be called if there are no
  // instances of the window.
  void UnregisterWindowClass();

 private:
  WindowClassRegistrar() = default;

  static WindowClassRegistrar* instance_;

  bool class_registered_ = false;
};

WindowClassRegistrar* WindowClassRegistrar::instance_ = nullptr;

const wchar_t* WindowClassRegistrar::GetWindowClass() {
  if (!class_registered_) {
    WNDCLASS window_class{};
    window_class.hCursor = LoadCursor(nullptr, IDC_ARROW);
    window_class.lpszClassName = kWindowClassName;
    window_class.style = CS_HREDRAW | CS_VREDRAW;
    window_class.cbClsExtra = 0;
    window_class.cbWndExtra = 0;
    window_class.hInstance = GetModuleHandle(nullptr);
    window_class.hIcon =
        LoadIcon(window_class.hInstance, MAKEINTRESOURCE(IDI_APP_ICON));
    window_class.hbrBackground = 0;
    window_class.lpszMenuName = nullptr;
    window_class.lpfnWndProc = Win32Window::WndProc;
    RegisterClass(&window_class);
    class_registered_ = true;
  }
  return kWindowClassName;
}

void WindowClassRegistrar::UnregisterWindowClass() {
  UnregisterClass(kWindowClassName, nullptr);
  class_registered_ = false;
}

Win32Window::Win32Window() {
  ++g_active_window_count;
}

Win32Window::~Win32Window() {
  --g_active_window_count;
  Destroy();
}

bool Win32Window::Create(const std::wstring& title,
                         const Point& origin,
                         const Size& size) {
  Destroy();

  const wchar_t* window_class =
      WindowClassRegistrar::GetInstance()->GetWindowClass();

  const POINT target_point = {static_cast<LONG>(origin.x),
                              static_cast<LONG>(origin.y)};
  HMONITOR monitor = MonitorFromPoint(target_point, MONITOR_DEFAULTTONEAREST);
  UINT dpi = FlutterDesktopGetDpiForMonitor(monitor);
  double scale_factor = dpi / 96.0;

  HWND window = CreateWindow(
      window_class, title.c_str(), WS_OVERLAPPEDWINDOW,
      Scale(origin.x, scale_factor), Scale(origin.y, scale_factor),
      Scale(size.width, scale_factor), Scale(size.height, scale_factor),
      nullptr, nullptr, GetModuleHandle(nullptr), this);

  if (!window) {
    return false;
  }

  UpdateTheme(window);
  EnableFluentBackdrop(window);
  ApplyWindowIcons(window);

  return OnCreate();
}

bool Win32Window::Show() {
  return ShowWindow(window_handle_, SW_SHOWNORMAL);
}

// static
LRESULT CALLBACK Win32Window::WndProc(HWND const window,
                                      UINT const message,
                                      WPARAM const wparam,
                                      LPARAM const lparam) noexcept {
  if (message == WM_NCCREATE) {
    auto window_struct = reinterpret_cast<CREATESTRUCT*>(lparam);
    SetWindowLongPtr(window, GWLP_USERDATA,
                     reinterpret_cast<LONG_PTR>(window_struct->lpCreateParams));

    auto that = static_cast<Win32Window*>(window_struct->lpCreateParams);
    EnableFullDpiSupportIfAvailable(window);
    that->window_handle_ = window;
  } else if (Win32Window* that = GetThisFromHandle(window)) {
    return that->MessageHandler(window, message, wparam, lparam);
  }

  return DefWindowProc(window, message, wparam, lparam);
}

LRESULT
Win32Window::MessageHandler(HWND hwnd,
                            UINT const message,
                            WPARAM const wparam,
                            LPARAM const lparam) noexcept {
  switch (message) {
    case WM_DESTROY:
      window_handle_ = nullptr;
      Destroy();
      if (quit_on_close_) {
        PostQuitMessage(0);
      }
      return 0;

    case WM_GETMINMAXINFO: {
      auto info = reinterpret_cast<MINMAXINFO*>(lparam);
      HMONITOR monitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
      UINT dpi = FlutterDesktopGetDpiForMonitor(monitor);
      double scale_factor = dpi / 96.0;
      info->ptMinTrackSize.x = Scale(kMinWindowWidth, scale_factor);
      info->ptMinTrackSize.y = Scale(kMinWindowHeight, scale_factor);
      return 0;
    }

    case WM_DPICHANGED: {
      auto newRectSize = reinterpret_cast<RECT*>(lparam);
      LONG newWidth = newRectSize->right - newRectSize->left;
      LONG newHeight = newRectSize->bottom - newRectSize->top;

      SetWindowPos(hwnd, nullptr, newRectSize->left, newRectSize->top, newWidth,
                   newHeight, SWP_NOZORDER | SWP_NOACTIVATE);
      ApplyWindowIcons(hwnd);

      return 0;
    }
    case WM_SIZE: {
      RECT rect = GetClientArea();
      if (child_content_ != nullptr) {
        const int width = rect.right - rect.left;
        const int height = rect.bottom - rect.top;
        if (width <= 0 || height <= 0) {
          return 0;
        }
        // Size and position the child window.
        MoveWindow(child_content_, rect.left, rect.top, width, height, TRUE);
      }
      return 0;
    }

    case WM_ACTIVATE:
      if (child_content_ != nullptr) {
        SetFocus(child_content_);
      }
      return 0;

    case WM_DWMCOLORIZATIONCOLORCHANGED:
      UpdateTheme(hwnd);
      EnableFluentBackdrop(hwnd);
      ApplyWindowIcons(hwnd);
      return 0;
  }

  return DefWindowProc(window_handle_, message, wparam, lparam);
}

void Win32Window::Destroy() {
  OnDestroy();

  if (window_handle_) {
    DestroyWindow(window_handle_);
    window_handle_ = nullptr;
  }
  if (g_active_window_count == 0) {
    WindowClassRegistrar::GetInstance()->UnregisterWindowClass();
  }
}

Win32Window* Win32Window::GetThisFromHandle(HWND const window) noexcept {
  return reinterpret_cast<Win32Window*>(
      GetWindowLongPtr(window, GWLP_USERDATA));
}

void Win32Window::SetChildContent(HWND content) {
  child_content_ = content;
  SetParent(content, window_handle_);
  EnableFluentBackdrop(window_handle_);
  EnableFluentBackdrop(content);
  RECT frame = GetClientArea();

  MoveWindow(content, frame.left, frame.top, frame.right - frame.left,
             frame.bottom - frame.top, true);

  SetFocus(child_content_);
}

RECT Win32Window::GetClientArea() {
  RECT frame;
  GetClientRect(window_handle_, &frame);
  return frame;
}

HWND Win32Window::GetHandle() {
  return window_handle_;
}

void Win32Window::SetQuitOnClose(bool quit_on_close) {
  quit_on_close_ = quit_on_close;
}

bool Win32Window::OnCreate() {
  // No-op; provided for subclasses.
  return true;
}

void Win32Window::OnDestroy() {
  // No-op; provided for subclasses.
}

void Win32Window::UpdateTheme(HWND const window) {
  DWORD light_mode;
  DWORD light_mode_size = sizeof(light_mode);
  LSTATUS result = RegGetValue(HKEY_CURRENT_USER, kGetPreferredBrightnessRegKey,
                               kGetPreferredBrightnessRegValue,
                               RRF_RT_REG_DWORD, nullptr, &light_mode,
                               &light_mode_size);

  bool dark = false;
  if (result == ERROR_SUCCESS) {
    dark = light_mode == 0;
    BOOL enable_dark_mode = dark;
    DwmSetWindowAttribute(window, DWMWA_USE_IMMERSIVE_DARK_MODE,
                          &enable_dark_mode, sizeof(enable_dark_mode));
  }
  ApplyCaptionColors(window, dark);
}
