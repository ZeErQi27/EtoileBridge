using System.Globalization;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Text.Json.Serialization;
using Etoile.Lite.Parser;
using Etoile.Lite.Parser.Scenecontrol;
using Etoile.Lite.Parser.Scenecontrol.IO;
using Etoile.Lite.Parser.Types;

internal static class ChartConversion
{
    static ChartConversion()
    {
        // Keep Lite 0.1.7's own numeric converters and supply AOT-compatible metadata.
        GetScenecontrolOptions(null).TypeInfoResolver = ScenecontrolJsonContext.Default;
    }

    [UnsafeAccessor(UnsafeAccessorKind.StaticField, Name = "Options")]
    private static extern ref JsonSerializerOptions GetScenecontrolOptions(ScenecontrolService? _);

    internal static string? Run(string input, string outputAff, string outputScenecontrol)
    {
        var culture = CultureInfo.CurrentCulture;
        var uiCulture = CultureInfo.CurrentUICulture;
        CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;
        CultureInfo.CurrentUICulture = CultureInfo.InvariantCulture;

        try
        {
            var reader = new ArcaeaChartReader(File.ReadAllLines(input));
            reader.Parse();
            if (reader.ErrorListener.Errors.Count != 0)
            {
                return string.Join("\n", reader.ErrorListener.Errors.Take(5)
                    .Select(error => $"AFF line {error.LineNumber}: {error.Message}"));
            }

            var visitor = reader.Visitor;
            // Variable-width taps already have 1 ms arcs. Repair only the remaining zero-length ones.
            var events = visitor.Events.Select(ev =>
                ev is RawArc arc && arc.Timing == arc.EndTiming && arc.ArcTaps.Count > 0
                    ? arc with { EndTiming = checked(arc.EndTiming + 1) }
                    : ev).ToList();
            var serializer = new ArcCreateChartSerializer(
                visitor.AudioOffset, visitor.TimingPointDensityFactor, events, visitor.TimingGroups);
            var result = serializer.Serialize();
            if (result.IsError)
            {
                var (exception, scenecontrol) = result.ErrorValue;
                return $"Scenecontrol line {scenecontrol?.LineNumber}: {exception.Message}";
            }

            File.WriteAllText(outputAff, serializer.ChartResult);
            File.WriteAllText(outputScenecontrol, serializer.ScenecontrolSerializationResult);
            return null;
        }
        catch (Exception exception)
        {
            return exception.Message;
        }
        finally
        {
            CultureInfo.CurrentCulture = culture;
            CultureInfo.CurrentUICulture = uiCulture;
        }
    }
}

[JsonSourceGenerationOptions(GenerationMode = JsonSourceGenerationMode.Metadata, IncludeFields = true)]
[JsonSerializable(typeof(List<SerializedUnit>))]
[JsonSerializable(typeof(bool))]
[JsonSerializable(typeof(int))]
[JsonSerializable(typeof(long))]
[JsonSerializable(typeof(float))]
[JsonSerializable(typeof(double))]
[JsonSerializable(typeof(string))]
internal partial class ScenecontrolJsonContext : JsonSerializerContext;
