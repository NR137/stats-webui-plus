package de.nr137.statswebuiplus.assets;

import java.util.ArrayList;
import java.util.List;

/**
 * The end gateway's icon as the game draws the block: its shader (rendertype_end_portal.fsh) ported. The stars don't
 * sit on the block's faces but on the screen: the End sky in a dark colour, and 16 layers of the star texture, each
 * scaled, turned and drifting with the game time in its own colour. The icon shows a full block's outline filled with
 * that, 10 pictures a second in a seamless 15-second loop (the wiki's recording of it has 20 a second for a minute).
 */
final class EndGateway {
	/** The shader's colours, one per layer (the first also for the sky). */
	static final double[][] COLORS = {
		{0.022087, 0.098399, 0.110818}, {0.011892, 0.095924, 0.089485}, {0.027636, 0.101689, 0.100326},
		{0.046564, 0.109883, 0.114838}, {0.064901, 0.117696, 0.097189}, {0.063761, 0.086895, 0.123646},
		{0.084817, 0.111994, 0.166380}, {0.097489, 0.154120, 0.091064}, {0.106152, 0.131144, 0.195191},
		{0.097721, 0.110188, 0.187229}, {0.133516, 0.138278, 0.148582}, {0.070006, 0.243332, 0.235792},
		{0.196766, 0.142899, 0.214696}, {0.047281, 0.315338, 0.321970}, {0.204675, 0.390010, 0.302066},
		{0.080955, 0.314821, 0.661491}};

	/** The end gateway's layers (the end portal has 15). */
	static final int LAYERS = 16;
	/** A picture every this many game ticks (10 a second). */
	static final int STEP = 2;
	/** The loop's length in game ticks (15 seconds). */
	static final int TICKS = 300;
	/**
	 * The screen the block is seen on, in icon pixels: square (the shader spreads its stars over the screen's width
	 * and height alike, so only a square window shows them square, as the wiki's recording does), the block 300 of
	 * 960 pixels wide: the size whose stars match the recording in size and number.
	 */
	static final double SCREEN_WIDTH = 960 * 32 / 300.0;
	static final double SCREEN_HEIGHT = SCREEN_WIDTH;
	/**
	 * Red, green and blue as in the wiki's recording of the block, which is darker and bluer than the bare shader
	 * (the End's fog, which the shader mixes in by distance, and the recording's brightness setting).
	 */
	static final double[] RECORDING = {0.66, 0.69, 0.94};
	/** Samples per icon pixel and direction: the stars are smaller than an icon pixel. */
	static final int SAMPLES = 8;

	private EndGateway() {
	}

	/** The frames of the icon (a full block's outline on a 32 x 32 picture). */
	static List<Apng.Frame> frames(Pixels sky, Pixels stars) {
		return frames(sky, stars, STEP, TICKS);
	}

	/** The same with a picture every step ticks, for ticks ticks. */
	static List<Apng.Frame> frames(Pixels sky, Pixels stars, int step, int ticks) {
		Pixels face = new Pixels(16, 16);
		java.util.Arrays.fill(face.argb, 0xFFFFFFFF);
		Pixels outline = Pixels.iso(face, face, face, 16);
		List<Apng.Frame> frames = new ArrayList<>();
		int fade = Math.min(FADE, ticks / 2);
		for (int tick = 0; tick < ticks; tick += step) {
			// The game's time of day as the shader gets it: 0 to 1 over a day of 24000 ticks.
			Pixels picture = picture(sky, stars, outline, tick / 24000.0);
			// The layers only line up again after two game days, so the loop's end blends into the picture one loop
			// earlier, which is where the loop starts again: the stars fade back instead of jumping.
			if (tick > ticks - fade) {
				double share = (double) (tick - (ticks - fade)) / fade;
				picture = mix(picture, picture(sky, stars, outline, (tick - ticks) / 24000.0), share);
			}
			frames.add(new Apng.Frame(picture, step * AnimatedTexture.TICK));
		}
		return frames;
	}

	/** Ticks at the loop's end over which it fades into its start. */
	static final int FADE = 60;

	/** Each colour channel share of the way from a to b. */
	private static Pixels mix(Pixels a, Pixels b, double share) {
		Pixels out = new Pixels(a.width, a.height);
		for (int i = 0; i < a.argb.length; i++) {
			int pixel = a.argb[i] & 0xFF000000;
			for (int shift = 16; shift >= 0; shift -= 8) {
				double value = (1 - share) * (a.argb[i] >> shift & 255) + share * (b.argb[i] >> shift & 255);
				pixel |= (int) Math.round(value) << shift;
			}
			out.argb[i] = pixel;
		}
		return out;
	}

	private static Pixels picture(Pixels sky, Pixels stars, Pixels outline, double gameTime) {
		Pixels picture = new Pixels(32, 32);
		double left = SCREEN_WIDTH / 2 - 16;
		double top = SCREEN_HEIGHT / 2 - 16;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				if (Pixels.alpha(outline.get(x, y)) == 0) {
					continue;
				}
				double[] sum = new double[3];
				for (int sy = 0; sy < SAMPLES; sy++) {
					for (int sx = 0; sx < SAMPLES; sx++) {
						double u = (left + x + (sx + 0.5) / SAMPLES) / SCREEN_WIDTH;
						double v = 1 - (top + y + (sy + 0.5) / SAMPLES) / SCREEN_HEIGHT;
						double[] color = shade(sky, stars, u, v, gameTime);
						for (int c = 0; c < 3; c++) {
							sum[c] += Math.min(1, color[c]);
						}
					}
				}
				int pixel = 0xFF000000;
				for (int c = 0; c < 3; c++) {
					pixel |= (int) Math.round(sum[c] / (SAMPLES * SAMPLES) * RECORDING[c] * 255) << (16 - 8 * c);
				}
				picture.set(x, y, pixel);
			}
		}
		return picture;
	}

	/** The shader at screen position u, v (0 to 1 across the screen). */
	static double[] shade(Pixels sky, Pixels stars, double u, double v, double gameTime) {
		double[] color = new double[3];
		add(color, sample(sky, u, v), COLORS[0]);
		for (int i = 0; i < LAYERS; i++) {
			double layer = i + 1;
			double scale = (4.5 - layer / 4.0) * 2.0;
			double angle = Math.toRadians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			// texProj0 * (scale rotate) * translate * SCALE_TRANSLATE, as GLSL multiplies a row vector.
			double rx = scale * (cos * u - sin * v);
			double ry = scale * (sin * u + cos * v);
			double tx = rx + 17.0 / layer;
			double ty = ry + (2.0 + layer / 1.5) * (gameTime * 1.5);
			add(color, sample(stars, 0.5 * tx + 0.25, 0.5 * ty + 0.25), COLORS[i]);
		}
		return color;
	}

	private static void add(double[] color, int pixel, double[] tint) {
		for (int c = 0; c < 3; c++) {
			color[c] += (pixel >> (16 - 8 * c) & 255) / 255.0 * tint[c];
		}
	}

	/** A texture at u, v (0 to 1, repeated), its nearest pixel as the game samples it. */
	private static int sample(Pixels texture, double u, double v) {
		int x = Math.floorMod((int) Math.floor(u * texture.width), texture.width);
		int y = Math.floorMod((int) Math.floor(v * texture.height), texture.height);
		return texture.get(x, y);
	}
}
