namespace GameController.Companion.Logging;

/// <summary>
/// The log on disk, next to the settings file, for troubleshooting after the window is gone. Keeps the
/// current file under <see cref="MaxBytes"/> and one older file (.1). Never throws: a log that cannot
/// be written must not take the companion down.
/// </summary>
public sealed class FileLog : IDisposable
{
    public const long MaxBytes = 1_000_000;
    readonly object _gate = new();
    StreamWriter? _w;

    public string Path { get; }

    public FileLog(string path)
    {
        Path = path;
        Open();
    }

    /// <summary>companion.log in the same folder as the settings file; a second copy (--allow-second) writes companion-second.log.</summary>
    public static string DefaultPath(bool secondInstance = false) =>
        System.IO.Path.Combine(System.IO.Path.GetDirectoryName(Config.ConfigStore.DefaultPath())!, secondInstance ? "companion-second.log" : "companion.log");

    public void Write(DateTimeOffset at, string level, string message)
    {
        lock (_gate)
        {
            try
            {
                if (_w == null) return;
                _w.WriteLine($"{at:yyyy-MM-dd HH:mm:ss.fff zzz} {level,-5} {message}");
                if (_w.BaseStream.Length > MaxBytes) Rotate();
            }
            catch { }
        }
    }

    void Open()
    {
        try
        {
            var dir = System.IO.Path.GetDirectoryName(Path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            _w = new StreamWriter(new FileStream(Path, FileMode.Append, FileAccess.Write, FileShare.ReadWrite | FileShare.Delete)) { AutoFlush = true };
        }
        catch { _w = null; }
    }

    void Rotate()
    {
        _w?.Dispose();
        _w = null;
        try { File.Move(Path, Path + ".1", overwrite: true); }
        catch (Exception e)
        {
            // .1 is locked or not a file: start this one over rather than grow past the cap.
            try
            {
                _w = new StreamWriter(new FileStream(Path, FileMode.Create, FileAccess.Write, FileShare.ReadWrite | FileShare.Delete)) { AutoFlush = true };
                _w.WriteLine($"{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss.fff zzz} warn  Log restarted: {Path}.1 could not be replaced ({e.Message})");
            }
            catch { _w = null; } // cannot even truncate: stop writing
            return;
        }
        Open();
    }

    public void Dispose()
    {
        lock (_gate) { try { _w?.Dispose(); } catch { } _w = null; }
    }
}
