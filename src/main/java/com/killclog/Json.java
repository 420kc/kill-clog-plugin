package com.killclog;

import com.google.gson.*;
import java.util.*;
import javax.annotation.Nullable;

/**
 * Reading a site's JSON where any field can be missing, JSON null or the wrong shape: each reads as absent (null, 0,
 * -1 or nothing to loop over) instead of throwing, so one odd field never costs the whole answer. One exception:
 * {@link #intField} on text that isn't a number throws, as Gson does, so callers read inside their parse's catch.
 */
final class Json
{
	private Json()
	{
	}

	@Nullable
	static JsonElement get(@Nullable JsonObject object, String field)
	{
		return object != null ? object.get(field) : null;
	}

	/** Whether the field is there and isn't JSON null, whatever its shape. */
	static boolean present(@Nullable JsonObject object, String field)
	{
		JsonElement element = get(object, field);
		return element != null && !element.isJsonNull();
	}

	@Nullable
	static JsonObject object(@Nullable JsonElement element)
	{
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	@Nullable
	static JsonObject object(@Nullable JsonObject object, String field)
	{
		return object(get(object, field));
	}

	@Nullable
	static JsonArray array(@Nullable JsonObject object, String field)
	{
		JsonElement element = get(object, field);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	/** The objects in an array, anything else in it skipped; none when it isn't one. */
	static List<JsonObject> objects(@Nullable JsonElement array)
	{
		List<JsonObject> objects = new ArrayList<>();
		for (JsonElement element : array != null && array.isJsonArray() ? array.getAsJsonArray() : new JsonArray())
		{
			if (element.isJsonObject())
			{
				objects.add(element.getAsJsonObject());
			}
		}
		return objects;
	}

	/** An object's fields, none when it isn't one. */
	static Set<Map.Entry<String, JsonElement>> entries(@Nullable JsonElement element)
	{
		JsonObject object = object(element);
		return object != null ? object.entrySet() : Collections.emptySet();
	}

	@Nullable
	static JsonPrimitive primitive(@Nullable JsonElement element)
	{
		return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive() : null;
	}

	/** A JSON number's value, or -1 for anything else. */
	static double number(@Nullable JsonElement element)
	{
		JsonPrimitive value = primitive(element);
		return value != null && value.isNumber() ? value.getAsDouble() : -1;
	}

	static int intField(@Nullable JsonObject object, String field)
	{
		JsonPrimitive value = primitive(get(object, field));
		return value != null ? value.getAsInt() : 0;
	}

	@Nullable
	static String stringField(@Nullable JsonObject object, String field)
	{
		JsonPrimitive value = primitive(get(object, field));
		return value != null ? value.getAsString() : null;
	}
}
