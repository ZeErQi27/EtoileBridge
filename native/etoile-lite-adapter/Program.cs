if (args.Length != 3)
{
    Console.Error.WriteLine("Usage: EtoileBridge.Lite <input.aff> <output.aff> <output.sc.json>");
    return 2;
}

var error = ChartConversion.Run(args[0], args[1], args[2]);
if (error is null) return 0;
Console.Error.WriteLine(error);
return 1;
