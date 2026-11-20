using System.Runtime.InteropServices;
using System.Text;

internal static class AndroidExports
{
    [UnmanagedCallersOnly(EntryPoint = "etoile_lite_convert")]
    private static unsafe int Convert(nint input, nint outputAff, nint outputScenecontrol, byte* error, int capacity)
    {
        string? message;
        try
        {
            message = ChartConversion.Run(
                Marshal.PtrToStringUTF8(input)!,
                Marshal.PtrToStringUTF8(outputAff)!,
                Marshal.PtrToStringUTF8(outputScenecontrol)!);
        }
        catch (Exception exception)
        {
            message = exception.GetBaseException().Message;
        }

        if (message is null) return 0;
        if (error != null && capacity > 0)
        {
            var buffer = new Span<byte>(error, capacity);
            Encoding.UTF8.GetEncoder().Convert(
                message.AsSpan(), buffer[..^1], true, out _, out var bytes, out _);
            buffer[bytes] = 0;
        }
        return 1;
    }
}
