using System.Globalization;
using Etoile.Lite.Parser;
using Etoile.Lite.Parser.Types;

if (args.Length != 3)
{
    Console.Error.WriteLine("Usage: EtoileBridge.Lite <input.aff> <output.aff> <output.sc.json>");
    return 2;
}

CultureInfo.CurrentCulture = CultureInfo.InvariantCulture;
CultureInfo.CurrentUICulture = CultureInfo.InvariantCulture;

try
{
    var reader = new ArcaeaChartReader(File.ReadAllLines(args[0]));
    reader.Parse();
    if (reader.ErrorListener.Errors.Count != 0)
    {
        foreach (var error in reader.ErrorListener.Errors.Take(5))
            Console.Error.WriteLine($"AFF line {error.LineNumber}: {error.Message}");
        return 1;
    }

    var visitor = reader.Visitor;
    // Lite already makes its variable-width taps 1 ms long. Only fix regular arcs it leaves at 0 ms.
    var events = visitor.Events.Select(ev =>
        ev is RawArc arc && arc.Timing == arc.EndTiming && arc.ArcTaps.Count > 0
            ? arc with { EndTiming = checked(arc.EndTiming + 1) }
            : ev);
    var serializer = new ArcCreateChartSerializer(
        visitor.AudioOffset, visitor.TimingPointDensityFactor, events.ToList(), visitor.TimingGroups);
    var result = serializer.Serialize();
    if (result.IsError)
    {
        var (exception, scenecontrol) = result.ErrorValue;
        Console.Error.WriteLine($"Scenecontrol line {scenecontrol?.LineNumber}: {exception.Message}");
        return 1;
    }

    File.WriteAllText(args[1], serializer.ChartResult);
    File.WriteAllText(args[2], serializer.ScenecontrolSerializationResult);
    return 0;
}
catch (Exception exception)
{
    Console.Error.WriteLine(exception.Message);
    return 1;
}
