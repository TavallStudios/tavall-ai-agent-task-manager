using System.Text.Json;
using System.Text.Json.Serialization;

namespace AgentTaskManager.Desktop.Services;

public static class DesktopJson
{
    public static readonly JsonSerializerOptions Default = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        PropertyNameCaseInsensitive = true,
        WriteIndented = true
    };

    static DesktopJson()
    {
        Default.Converters.Add(new UnixEpochDateTimeOffsetJsonConverter());
        Default.Converters.Add(new NullableUnixEpochDateTimeOffsetJsonConverter());
    }
}

internal sealed class UnixEpochDateTimeOffsetJsonConverter : JsonConverter<DateTimeOffset>
{
    private const long TicksPerSecond = TimeSpan.TicksPerSecond;

    public override DateTimeOffset Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        => reader.TokenType switch
        {
            JsonTokenType.String => DateTimeOffset.Parse(reader.GetString() ?? throw new JsonException("Date value was null.")),
            JsonTokenType.Number => ReadUnixEpoch(ref reader),
            _ => throw new JsonException($"Unsupported date token '{reader.TokenType}'.")
        };

    public override void Write(Utf8JsonWriter writer, DateTimeOffset value, JsonSerializerOptions options)
        => writer.WriteStringValue(value);

    private static DateTimeOffset ReadUnixEpoch(ref Utf8JsonReader reader)
    {
        decimal epochSeconds = reader.GetDecimal();
        long wholeSeconds = decimal.ToInt64(decimal.Truncate(epochSeconds));
        decimal fractionalSeconds = epochSeconds - wholeSeconds;
        long fractionalTicks = decimal.ToInt64(decimal.Round(
            fractionalSeconds * TicksPerSecond,
            0,
            MidpointRounding.AwayFromZero));
        return DateTimeOffset.FromUnixTimeSeconds(wholeSeconds).AddTicks(fractionalTicks);
    }
}

internal sealed class NullableUnixEpochDateTimeOffsetJsonConverter : JsonConverter<DateTimeOffset?>
{
    private readonly UnixEpochDateTimeOffsetJsonConverter _inner = new();

    public override DateTimeOffset? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        => reader.TokenType == JsonTokenType.Null
            ? null
            : _inner.Read(ref reader, typeof(DateTimeOffset), options);

    public override void Write(Utf8JsonWriter writer, DateTimeOffset? value, JsonSerializerOptions options)
    {
        if (value is null)
        {
            writer.WriteNullValue();
            return;
        }

        _inner.Write(writer, value.Value, options);
    }
}
