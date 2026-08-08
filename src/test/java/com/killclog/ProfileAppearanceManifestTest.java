package com.killclog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.PlayerComposition;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileAppearanceManifestTest
{
	private final Gson gson = new Gson();

	@Test
	public void capturesOnlyTheFixedDataRecipe()
	{
		int[] equipment = {512, 513, 514, 515, 516, 517, 518, 519, 520, 521, -1, 0};
		int[] colors = {1, 2, 3, 4, 5};
		ColorTextureOverride[] overrides = new ColorTextureOverride[12];
		overrides[3] = override(new short[]{12, -7}, new short[]{4});

		ProfileAppearanceManifest manifest = ProfileAppearanceManifest.capture(
			composition(equipment, colors, overrides, 0, -1), 233, "2.1.0", 9399);

		assertNotNull(manifest);
		JsonObject json = gson.toJsonTree(manifest).getAsJsonObject();
		assertEquals(9, json.size());
		assertEquals("killclog.appearance.v2", json.get("schema").getAsString());
		assertEquals("233", json.get("game_build").getAsString());
		assertEquals("2.1.0", json.get("client_version").getAsString());
		assertEquals(0, json.get("gender").getAsInt());
		assertEquals(-1, json.get("transformed_npc_id").getAsInt());
		assertEquals(9399, json.get("follower_npc_id").getAsInt());
		assertArrayEquals(equipment, ints(json.getAsJsonArray("equipment")));
		assertArrayEquals(colors, ints(json.getAsJsonArray("colors")));
		assertEquals(12, json.getAsJsonArray("overrides").size());
		JsonObject itemOverride = json.getAsJsonArray("overrides").get(3).getAsJsonObject();
		assertArrayEquals(new int[]{12, -7}, ints(itemOverride.getAsJsonArray("colors")));
		assertArrayEquals(new int[]{4}, ints(itemOverride.getAsJsonArray("textures")));
	}

	@Test
	public void treatsNoRuntimeOverridesAsTwelveNullSlots()
	{
		ProfileAppearanceManifest manifest = ProfileAppearanceManifest.capture(
			composition(new int[12], new int[5], null, 1, -1), 233, "2.1.0", -1);

		assertNotNull(manifest);
		JsonArray overrides = gson.toJsonTree(manifest).getAsJsonObject()
			.getAsJsonArray("overrides");
		assertEquals(12, overrides.size());
		for (int i = 0; i < overrides.size(); i++)
		{
			assertTrue(overrides.get(i).isJsonNull());
		}
	}

	@Test
	public void rejectsTransformsAndMalformedCompositionShapes()
	{
		assertNull(ProfileAppearanceManifest.capture(
			composition(new int[12], new int[5], null, 0, 42), 233, "2.1.0", -1));
		assertNull(ProfileAppearanceManifest.capture(
			composition(new int[11], new int[5], null, 0, -1), 233, "2.1.0", -1));
		assertNull(ProfileAppearanceManifest.capture(
			composition(new int[12], new int[5], null, 7, -1), 233, "2.1.0", -1));
		assertNull(ProfileAppearanceManifest.capture(
			composition(new int[12], new int[5], null, 0, -1), 233, "2.1.0", -2));
	}

	private static int[] ints(JsonArray values)
	{
		int[] result = new int[values.size()];
		for (int i = 0; i < values.size(); i++)
		{
			result[i] = values.get(i).getAsInt();
		}
		return result;
	}

	private static ColorTextureOverride override(short[] colors, short[] textures)
	{
		return (ColorTextureOverride) Proxy.newProxyInstance(
			ColorTextureOverride.class.getClassLoader(),
			new Class<?>[]{ColorTextureOverride.class},
			(proxy, method, args) -> "getColorToReplaceWith".equals(method.getName())
				? colors : textures);
	}

	private static PlayerComposition composition(int[] equipment, int[] colors,
		ColorTextureOverride[] overrides, int gender, int transformedNpcId)
	{
		return (PlayerComposition) Proxy.newProxyInstance(
			PlayerComposition.class.getClassLoader(),
			new Class<?>[]{PlayerComposition.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getEquipmentIds":
						return equipment;
					case "getColors":
						return colors;
					case "getColorTextureOverrides":
						return overrides;
					case "getGender":
						return gender;
					case "getTransformedNpcId":
						return transformedNpcId;
					case "isFemale":
						return gender == 1;
					default:
						return null;
				}
			});
	}
}
