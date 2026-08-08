package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.runelite.api.Model;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

/** Small software portrait renderer for the local player's live equipped model. */
final class ProfileCardPlayerModel
{
	static final int WIDTH = 176;
	static final int HEIGHT = 252;

	private static final int SCALE = 2;
	private static final int MARGIN = 8 * SCALE;
	private static final double YAW = Math.toRadians(-28);
	private static final double PITCH = Math.toRadians(5);

	private ProfileCardPlayerModel()
	{
	}

	@Nullable
	static Snapshot snapshot(Model model, TextureProvider textureProvider, int gameCycle)
	{
		float[] sourceX = model.getVerticesX();
		float[] sourceY = model.getVerticesY();
		float[] sourceZ = model.getVerticesZ();
		int[] sourceA = model.getFaceIndices1();
		int[] sourceB = model.getFaceIndices2();
		int[] sourceC = model.getFaceIndices3();
		int[] sourceColors1 = model.getFaceColors1();
		int[] sourceColors2 = model.getFaceColors2();
		int[] sourceColors3 = model.getFaceColors3();
		int vertices = Math.min(model.getVerticesCount(),
			Math.min(sourceX.length, Math.min(sourceY.length, sourceZ.length)));
		int faces = Math.min(model.getFaceCount(),
			Math.min(sourceA.length, Math.min(sourceB.length, Math.min(sourceC.length,
				Math.min(sourceColors1.length, Math.min(sourceColors2.length, sourceColors3.length))))));
		if (vertices < 3 || faces < 1)
		{
			return null;
		}
		float[] textureU = new float[faces * 3];
		float[] textureV = new float[faces * 3];
		computeFaceUvs(model, faces, textureU, textureV);
		return new Snapshot(
			copy(sourceX, vertices),
			copy(sourceY, vertices),
			copy(sourceZ, vertices),
			copy(sourceA, faces),
			copy(sourceB, faces),
			copy(sourceC, faces),
			copy(sourceColors1, faces),
			copy(sourceColors2, faces),
			copy(sourceColors3, faces),
			copy(model.getUnlitFaceColors(), faces),
			copy(model.getFaceTextures(), faces),
			copy(model.getFaceTransparencies(), faces),
			textureU,
			textureV,
			copyTextures(model.getFaceTextures(), faces, textureProvider, gameCycle));
	}

	@Nullable
	static BufferedImage render(Snapshot model)
	{
		int width = WIDTH * SCALE;
		int height = HEIGHT * SCALE;
		double[] projectedX = new double[model.x.length];
		double[] projectedY = new double[model.x.length];
		double[] depth = new double[model.x.length];
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double sinYaw = Math.sin(YAW);
		double cosYaw = Math.cos(YAW);
		double sinPitch = Math.sin(PITCH);
		double cosPitch = Math.cos(PITCH);

		for (int i = 0; i < model.x.length; i++)
		{
			double rx = model.x[i] * cosYaw + model.z[i] * sinYaw;
			double rz = -model.x[i] * sinYaw + model.z[i] * cosYaw;
			double ry = model.y[i] * cosPitch - rz * sinPitch;
			projectedX[i] = rx;
			projectedY[i] = ry;
			depth[i] = model.y[i] * sinPitch + rz * cosPitch;
			minX = Math.min(minX, rx);
			maxX = Math.max(maxX, rx);
			minY = Math.min(minY, ry);
			maxY = Math.max(maxY, ry);
		}

		double modelWidth = Math.max(1d, maxX - minX);
		double modelHeight = Math.max(1d, maxY - minY);
		double fit = Math.min((width - 2d * MARGIN) / modelWidth,
			(height - 2d * MARGIN) / modelHeight);
		for (int i = 0; i < model.x.length; i++)
		{
			projectedX[i] = MARGIN + (projectedX[i] - minX) * fit;
			projectedY[i] = MARGIN + (projectedY[i] - minY) * fit;
		}

		List<Face> faces = new ArrayList<>(model.a.length);
		for (int i = 0; i < model.a.length; i++)
		{
			int a = model.a[i];
			int b = model.b[i];
			int c = model.c[i];
			if (a < 0 || b < 0 || c < 0
				|| a >= model.x.length || b >= model.x.length || c >= model.x.length)
			{
				continue;
			}
			Color color = faceColor(model, i);
			if (color != null)
			{
				faces.add(new Face(a, b, c, i,
					(depth[a] + depth[b] + depth[c]) / 3d, color));
			}
		}
		faces.sort(Comparator.comparingDouble(face -> face.depth));

		BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) painted.getRaster().getDataBuffer()).getData();
		int paintedFaces = 0;
		for (Face face : faces)
		{
			if (paintFace(pixels, width, height, projectedX, projectedY, model, face))
			{
				paintedFaces++;
			}
		}
		if (paintedFaces == 0)
		{
			return null;
		}

		BufferedImage outlined = outline(painted);
		BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(outlined, 0, 0, WIDTH, HEIGHT, null);
		g.dispose();
		return result;
	}

	private static boolean paintFace(int[] pixels, int width, int height,
		double[] projectedX, double[] projectedY, Snapshot model, Face face)
	{
		double ax = projectedX[face.a];
		double ay = projectedY[face.a];
		double bx = projectedX[face.b];
		double by = projectedY[face.b];
		double cx = projectedX[face.c];
		double cy = projectedY[face.c];
		double denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
		if (Math.abs(denominator) < .0001d)
		{
			return false;
		}

		int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
		int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
		int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
		int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
		TextureData texture = texture(model, face.index);
		boolean painted = false;
		for (int y = minY; y <= maxY; y++)
		{
			for (int x = minX; x <= maxX; x++)
			{
				double sampleX = x + .5d;
				double sampleY = y + .5d;
				double weightA = ((by - cy) * (sampleX - cx)
					+ (cx - bx) * (sampleY - cy)) / denominator;
				double weightB = ((cy - ay) * (sampleX - cx)
					+ (ax - cx) * (sampleY - cy)) / denominator;
				double weightC = 1d - weightA - weightB;
				if (weightA < -.0001d || weightB < -.0001d || weightC < -.0001d)
				{
					continue;
				}

				int source = face.color.getRGB();
				if (texture != null)
				{
					source = textureColor(model, face.index, texture,
						weightA, weightB, weightC, face.color.getAlpha());
					if ((source >>> 24) == 0)
					{
						continue;
					}
				}
				int pixel = y * width + x;
				pixels[pixel] = blend(pixels[pixel], source);
				painted = true;
			}
		}
		return painted;
	}

	@Nullable
	private static TextureData texture(Snapshot model, int face)
	{
		if (model.textures == null || model.textures[face] == -1)
		{
			return null;
		}
		return model.textureData.get(model.textures[face] & 0xFFFF);
	}

	private static int textureColor(Snapshot model, int face, TextureData texture,
		double weightA, double weightB, double weightC, int alpha)
	{
		int offset = face * 3;
		double u = model.textureU[offset] * weightA
			+ model.textureU[offset + 1] * weightB
			+ model.textureU[offset + 2] * weightC
			+ texture.offsetU;
		double v = model.textureV[offset] * weightA
			+ model.textureV[offset + 1] * weightB
			+ model.textureV[offset + 2] * weightC
			+ texture.offsetV;
		int textureX = wrap((int) Math.floor(u * texture.size), texture.size);
		int textureY = wrap((int) Math.floor(v * texture.size), texture.size);
		int rgb = texture.pixels[textureY * texture.size + textureX];
		if ((rgb & 0xFFFFFF) == 0)
		{
			return 0;
		}

		int color1 = model.colors1[face];
		int color2 = model.colors3[face] == -1 ? color1 : model.colors2[face];
		int color3 = model.colors3[face] == -1 ? color1 : model.colors3[face];
		double light = Math.max(0d, Math.min(1d,
			(color1 * weightA + color2 * weightB + color3 * weightC) / 127d));
		int red = (int) Math.round(((rgb >> 16) & 0xFF) * light);
		int green = (int) Math.round(((rgb >> 8) & 0xFF) * light);
		int blue = (int) Math.round((rgb & 0xFF) * light);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int blend(int destination, int source)
	{
		int sourceAlpha = source >>> 24;
		if (sourceAlpha == 255)
		{
			return source;
		}
		int destinationAlpha = destination >>> 24;
		int inverse = 255 - sourceAlpha;
		int alpha = sourceAlpha + (destinationAlpha * inverse + 127) / 255;
		if (alpha == 0)
		{
			return 0;
		}
		int red = (((source >> 16) & 0xFF) * sourceAlpha
			+ ((destination >> 16) & 0xFF) * destinationAlpha * inverse / 255) / alpha;
		int green = (((source >> 8) & 0xFF) * sourceAlpha
			+ ((destination >> 8) & 0xFF) * destinationAlpha * inverse / 255) / alpha;
		int blue = ((source & 0xFF) * sourceAlpha
			+ (destination & 0xFF) * destinationAlpha * inverse / 255) / alpha;
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int wrap(int value, int size)
	{
		int wrapped = value % size;
		return wrapped < 0 ? wrapped + size : wrapped;
	}

	private static BufferedImage outline(BufferedImage source)
	{
		BufferedImage result = new BufferedImage(
			source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int[] pixels = source.getRGB(0, 0, source.getWidth(), source.getHeight(),
			null, 0, source.getWidth());
		int[] outlined = new int[pixels.length];
		int radius = 3;
		for (int y = 0; y < source.getHeight(); y++)
		{
			for (int x = 0; x < source.getWidth(); x++)
			{
				int index = y * source.getWidth() + x;
				if ((pixels[index] >>> 24) != 0)
				{
					continue;
				}
				boolean neighbor = false;
				for (int dy = -radius; dy <= radius && !neighbor; dy++)
				{
					int sampleY = y + dy;
					if (sampleY < 0 || sampleY >= source.getHeight())
					{
						continue;
					}
					for (int dx = -radius; dx <= radius; dx++)
					{
						int sampleX = x + dx;
						if (sampleX >= 0 && sampleX < source.getWidth()
							&& (pixels[sampleY * source.getWidth() + sampleX] >>> 24) > 24)
						{
							neighbor = true;
							break;
						}
					}
				}
				if (neighbor)
				{
					outlined[index] = 0x78000000;
				}
			}
		}
		result.setRGB(0, 0, source.getWidth(), source.getHeight(),
			outlined, 0, source.getWidth());
		Graphics2D g = result.createGraphics();
		g.setComposite(AlphaComposite.SrcOver);
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return result;
	}

	@Nullable
	private static Color faceColor(Snapshot model, int face)
	{
		if (model.colors3[face] == -2)
		{
			return null;
		}
		int transparency = model.transparency != null
			? model.transparency[face] & 0xFF : 0;
		int alpha = 255 - transparency;
		if (alpha <= 8)
		{
			return null;
		}

		boolean textured = model.textures != null && model.textures[face] != -1;
		if (textured && model.unlitColors != null)
		{
			return color(model.unlitColors[face] & 0xFFFF, alpha);
		}
		int color1 = model.colors1[face];
		if (color1 < 0 && model.unlitColors != null)
		{
			color1 = model.unlitColors[face] & 0xFFFF;
		}
		if (color1 < 0)
		{
			return null;
		}
		if (model.colors3[face] == -1)
		{
			return color(color1, alpha);
		}
		Color first = color(color1, alpha);
		Color second = color(model.colors2[face], alpha);
		Color third = color(model.colors3[face], alpha);
		return new Color(
			(first.getRed() + second.getRed() + third.getRed()) / 3,
			(first.getGreen() + second.getGreen() + third.getGreen()) / 3,
			(first.getBlue() + second.getBlue() + third.getBlue()) / 3, alpha);
	}

	private static Color color(int packedHsl, int alpha)
	{
		int value = packedHsl & 0xFFFF;
		double hue = ((value >> 10) & 0x3F) / 64d;
		double saturation = ((value >> 7) & 0x07) / 8d;
		double lightness = (value & 0x7F) / 128d;
		double red;
		double green;
		double blue;
		if (saturation == 0d)
		{
			red = green = blue = lightness;
		}
		else
		{
			double q = lightness < .5d
				? lightness * (1d + saturation)
				: lightness + saturation - lightness * saturation;
			double p = 2d * lightness - q;
			red = hueToRgb(p, q, hue + 1d / 3d);
			green = hueToRgb(p, q, hue);
			blue = hueToRgb(p, q, hue - 1d / 3d);
		}
		return new Color(channel(red), channel(green), channel(blue), alpha);
	}

	private static double hueToRgb(double p, double q, double t)
	{
		if (t < 0d)
		{
			t += 1d;
		}
		if (t > 1d)
		{
			t -= 1d;
		}
		if (t < 1d / 6d)
		{
			return p + (q - p) * 6d * t;
		}
		if (t < .5d)
		{
			return q;
		}
		if (t < 2d / 3d)
		{
			return p + (q - p) * (2d / 3d - t) * 6d;
		}
		return p;
	}

	private static int channel(double value)
	{
		return (int) Math.round(Math.max(0d, Math.min(1d, value)) * 255d);
	}

	private static void computeFaceUvs(Model model, int faces, float[] u, float[] v)
	{
		float[] vertexX = model.getVerticesX();
		float[] vertexY = model.getVerticesY();
		float[] vertexZ = model.getVerticesZ();
		int[] indices1 = model.getFaceIndices1();
		int[] indices2 = model.getFaceIndices2();
		int[] indices3 = model.getFaceIndices3();
		byte[] textureFaces = model.getTextureFaces();
		int[] texIndices1 = model.getTexIndices1();
		int[] texIndices2 = model.getTexIndices2();
		int[] texIndices3 = model.getTexIndices3();
		for (int face = 0; face < faces; face++)
		{
			setDefaultUvs(face, u, v);
			if (textureFaces == null || face >= textureFaces.length || textureFaces[face] == -1
				|| texIndices1 == null || texIndices2 == null || texIndices3 == null)
			{
				continue;
			}

			int textureFace = textureFaces[face] & 0xFF;
			if (textureFace >= texIndices1.length || textureFace >= texIndices2.length
				|| textureFace >= texIndices3.length)
			{
				continue;
			}
			int triangleA = indices1[face];
			int triangleB = indices2[face];
			int triangleC = indices3[face];
			int textureA = texIndices1[textureFace];
			int textureB = texIndices2[textureFace];
			int textureC = texIndices3[textureFace];
			if (!validVertex(vertexX, vertexY, vertexZ, triangleA)
				|| !validVertex(vertexX, vertexY, vertexZ, triangleB)
				|| !validVertex(vertexX, vertexY, vertexZ, triangleC)
				|| !validVertex(vertexX, vertexY, vertexZ, textureA)
				|| !validVertex(vertexX, vertexY, vertexZ, textureB)
				|| !validVertex(vertexX, vertexY, vertexZ, textureC))
			{
				continue;
			}

			float originX = vertexX[textureA];
			float originY = vertexY[textureA];
			float originZ = vertexZ[textureA];
			float basisUx = vertexX[textureB] - originX;
			float basisUy = vertexY[textureB] - originY;
			float basisUz = vertexZ[textureB] - originZ;
			float basisVx = vertexX[textureC] - originX;
			float basisVy = vertexY[textureC] - originY;
			float basisVz = vertexZ[textureC] - originZ;
			float crossX = basisUy * basisVz - basisUz * basisVy;
			float crossY = basisUz * basisVx - basisUx * basisVz;
			float crossZ = basisUx * basisVy - basisUy * basisVx;
			float dualUx = basisVy * crossZ - basisVz * crossY;
			float dualUy = basisVz * crossX - basisVx * crossZ;
			float dualUz = basisVx * crossY - basisVy * crossX;
			float uDivisor = dualUx * basisUx + dualUy * basisUy + dualUz * basisUz;
			float dualVx = basisUy * crossZ - basisUz * crossY;
			float dualVy = basisUz * crossX - basisUx * crossZ;
			float dualVz = basisUx * crossY - basisUy * crossX;
			float vDivisor = dualVx * basisVx + dualVy * basisVy + dualVz * basisVz;
			if (Math.abs(uDivisor) < .0001f || Math.abs(vDivisor) < .0001f)
			{
				continue;
			}

			int offset = face * 3;
			u[offset] = dot(vertexX[triangleA] - originX, vertexY[triangleA] - originY,
				vertexZ[triangleA] - originZ, dualUx, dualUy, dualUz) / uDivisor;
			u[offset + 1] = dot(vertexX[triangleB] - originX, vertexY[triangleB] - originY,
				vertexZ[triangleB] - originZ, dualUx, dualUy, dualUz) / uDivisor;
			u[offset + 2] = dot(vertexX[triangleC] - originX, vertexY[triangleC] - originY,
				vertexZ[triangleC] - originZ, dualUx, dualUy, dualUz) / uDivisor;
			v[offset] = dot(vertexX[triangleA] - originX, vertexY[triangleA] - originY,
				vertexZ[triangleA] - originZ, dualVx, dualVy, dualVz) / vDivisor;
			v[offset + 1] = dot(vertexX[triangleB] - originX, vertexY[triangleB] - originY,
				vertexZ[triangleB] - originZ, dualVx, dualVy, dualVz) / vDivisor;
			v[offset + 2] = dot(vertexX[triangleC] - originX, vertexY[triangleC] - originY,
				vertexZ[triangleC] - originZ, dualVx, dualVy, dualVz) / vDivisor;
		}
	}

	private static boolean validVertex(float[] x, float[] y, float[] z, int vertex)
	{
		return vertex >= 0 && vertex < x.length && vertex < y.length && vertex < z.length;
	}

	private static void setDefaultUvs(int face, float[] u, float[] v)
	{
		int offset = face * 3;
		u[offset] = 0f;
		v[offset] = 0f;
		u[offset + 1] = 1f;
		v[offset + 1] = 0f;
		u[offset + 2] = 0f;
		v[offset + 2] = 1f;
	}

	private static float dot(float ax, float ay, float az, float bx, float by, float bz)
	{
		return ax * bx + ay * by + az * bz;
	}

	private static Map<Integer, TextureData> copyTextures(@Nullable short[] faceTextures,
		int faces, TextureProvider textureProvider, int gameCycle)
	{
		Map<Integer, TextureData> result = new HashMap<>();
		if (faceTextures == null || textureProvider == null)
		{
			return result;
		}
		Texture[] textures = textureProvider.getTextures();
		for (int face = 0; face < Math.min(faces, faceTextures.length); face++)
		{
			if (faceTextures[face] == -1)
			{
				continue;
			}
			int textureId = faceTextures[face] & 0xFFFF;
			if (result.containsKey(textureId))
			{
				continue;
			}
			int[] pixels = textureProvider.load(textureId);
			if (pixels == null)
			{
				continue;
			}
			int size = (int) Math.round(Math.sqrt(pixels.length));
			if (size < 1 || size * size != pixels.length)
			{
				continue;
			}
			Texture texture = textures != null && textureId < textures.length
				? textures[textureId] : null;
			float offsetU = 0f;
			float offsetV = 0f;
			if (texture != null)
			{
				float offset = (gameCycle & 127) * texture.getAnimationSpeed() / (float) size;
				switch (texture.getAnimationDirection())
				{
					case 1:
						offsetV = -offset;
						break;
					case 2:
						offsetU = -offset;
						break;
					case 3:
						offsetV = offset;
						break;
					case 4:
						offsetU = offset;
						break;
					default:
						break;
				}
			}
			result.put(textureId, new TextureData(
				Arrays.copyOf(pixels, pixels.length), size, offsetU, offsetV));
		}
		return result;
	}

	private static float[] copy(float[] source, int length)
	{
		return Arrays.copyOf(source, length);
	}

	private static int[] copy(int[] source, int length)
	{
		return Arrays.copyOf(source, length);
	}

	@Nullable
	private static short[] copy(@Nullable short[] source, int length)
	{
		return source != null ? Arrays.copyOf(source, length) : null;
	}

	@Nullable
	private static byte[] copy(@Nullable byte[] source, int length)
	{
		return source != null ? Arrays.copyOf(source, length) : null;
	}

	static final class Snapshot
	{
		final float[] x;
		final float[] y;
		final float[] z;
		final int[] a;
		final int[] b;
		final int[] c;
		final int[] colors1;
		final int[] colors2;
		final int[] colors3;
		final short[] unlitColors;
		final short[] textures;
		final byte[] transparency;
		final float[] textureU;
		final float[] textureV;
		final Map<Integer, TextureData> textureData;

		Snapshot(float[] x, float[] y, float[] z,
			int[] a, int[] b, int[] c,
			int[] colors1, int[] colors2, int[] colors3,
			@Nullable short[] unlitColors, @Nullable short[] textures,
			@Nullable byte[] transparency)
		{
			this(x, y, z, a, b, c, colors1, colors2, colors3,
				unlitColors, textures, transparency,
				new float[a.length * 3], new float[a.length * 3], new HashMap<>());
			for (int face = 0; face < a.length; face++)
			{
				setDefaultUvs(face, textureU, textureV);
			}
		}

		Snapshot(float[] x, float[] y, float[] z,
			int[] a, int[] b, int[] c,
			int[] colors1, int[] colors2, int[] colors3,
			@Nullable short[] unlitColors, @Nullable short[] textures,
			@Nullable byte[] transparency, float[] textureU, float[] textureV,
			Map<Integer, TextureData> textureData)
		{
			this.x = x;
			this.y = y;
			this.z = z;
			this.a = a;
			this.b = b;
			this.c = c;
			this.colors1 = colors1;
			this.colors2 = colors2;
			this.colors3 = colors3;
			this.unlitColors = unlitColors;
			this.textures = textures;
			this.transparency = transparency;
			this.textureU = textureU;
			this.textureV = textureV;
			this.textureData = textureData;
		}
	}

	static final class TextureData
	{
		final int[] pixels;
		final int size;
		final float offsetU;
		final float offsetV;

		TextureData(int[] pixels, int size, float offsetU, float offsetV)
		{
			this.pixels = pixels;
			this.size = size;
			this.offsetU = offsetU;
			this.offsetV = offsetV;
		}
	}

	private static final class Face
	{
		private final int a;
		private final int b;
		private final int c;
		private final int index;
		private final double depth;
		private final Color color;

		private Face(int a, int b, int c, int index, double depth, Color color)
		{
			this.a = a;
			this.b = b;
			this.c = c;
			this.index = index;
			this.depth = depth;
			this.color = color;
		}
	}
}
