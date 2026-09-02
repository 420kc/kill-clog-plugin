package com.killclog;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.PlayerComposition;

/** Fixed, data-only PlayerComposition recipe accepted by killclog.com. */
final class ProfileAppearanceManifest
{
	static final String SCHEMA = "killclog.appearance.v3";
	static final int EQUIPMENT_SLOTS = 12;
	static final int BODY_COLORS = 5;

	private final String schema = SCHEMA;

	@SerializedName("game_build")
	private final String gameBuild;

	@SerializedName("client_version")
	private final String clientVersion;

	private final int gender;
	private final int[] equipment;
	private final int[] colors;
	private final Override[] overrides;

	@SerializedName("transformed_npc_id")
	private final int transformedNpcId;

	@SerializedName("follower_npc_id")
	private final int followerNpcId;

	@SerializedName("idle_pose_animation")
	private final int idlePoseAnimation;

	private ProfileAppearanceManifest(String gameBuild, String clientVersion, int gender,
		int[] equipment, int[] colors, Override[] overrides, int transformedNpcId,
		int followerNpcId, int idlePoseAnimation)
	{
		this.gameBuild = gameBuild;
		this.clientVersion = clientVersion;
		this.gender = gender;
		this.equipment = Arrays.copyOf(equipment, equipment.length);
		this.colors = Arrays.copyOf(colors, colors.length);
		this.overrides = Arrays.copyOf(overrides, overrides.length);
		this.transformedNpcId = transformedNpcId;
		this.followerNpcId = followerNpcId;
		this.idlePoseAnimation = idlePoseAnimation;
	}

	@Nullable
	static ProfileAppearanceManifest capture(PlayerComposition composition, int gameBuild,
		String clientVersion, int followerNpcId, int idlePoseAnimation)
	{
		if (composition == null || composition.getTransformedNpcId() != -1
			|| followerNpcId < -1 || idlePoseAnimation < 0 || idlePoseAnimation > 65_535)
		{
			return null;
		}
		int[] equipment = composition.getEquipmentIds();
		int[] colors = composition.getColors();
		ColorTextureOverride[] sourceOverrides = composition.getColorTextureOverrides();
		if (equipment == null || equipment.length != EQUIPMENT_SLOTS
			|| colors == null || colors.length != BODY_COLORS
			|| (sourceOverrides != null && sourceOverrides.length != EQUIPMENT_SLOTS))
		{
			return null;
		}
		int gender = composition.getGender();
		if (gender != 0 && gender != 1)
		{
			return null;
		}

		Override[] overrides = new Override[EQUIPMENT_SLOTS];
		for (int i = 0; sourceOverrides != null && i < sourceOverrides.length; i++)
		{
			ColorTextureOverride value = sourceOverrides[i];
			if (value != null)
			{
				overrides[i] = new Override(
					toInts(value.getColorToReplaceWith()),
					toInts(value.getTextureToReplaceWith()));
			}
		}
		return new ProfileAppearanceManifest(Integer.toString(gameBuild), clientVersion,
			gender, equipment, colors, overrides, -1, followerNpcId, idlePoseAnimation);
	}

	private static int[] toInts(short[] values)
	{
		if (values == null)
		{
			return new int[0];
		}
		int[] result = new int[values.length];
		for (int i = 0; i < values.length; i++)
		{
			result[i] = values[i];
		}
		return result;
	}

	private static final class Override
	{
		private final int[] colors;
		private final int[] textures;

		private Override(int[] colors, int[] textures)
		{
			this.colors = colors;
			this.textures = textures;
		}
	}
}
