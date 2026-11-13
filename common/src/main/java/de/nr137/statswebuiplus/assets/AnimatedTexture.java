package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A texture the game animates: its PNG is a strip of frames, its .mcmeta says in which order and how many ticks
 * (1/20 s) each shows, and whether the game blends from one into the next ("interpolate").
 */
final class AnimatedTexture {
	/** Milliseconds per game tick. */
	static final int TICK = 50;

	private final List<Pixels> frames = new ArrayList<>();
	private final List<Integer> ticks = new ArrayList<>();
	private final boolean interpolate;

	/** Null if the .mcmeta has no animation (a plain texture). */
	static AnimatedTexture of(Pixels strip, JsonObject meta) {
		if (meta == null || !(meta.get("animation") instanceof JsonObject animation)) {
			return null;
		}
		return new AnimatedTexture(strip, animation);
	}

	private AnimatedTexture(Pixels strip, JsonObject animation) {
		int width = animation.has("width") ? animation.get("width").getAsInt() : strip.width;
		int height = animation.has("height") ? animation.get("height").getAsInt()
			: animation.has("width") ? width : Math.min(strip.width, strip.height);
		int perRow = Math.max(1, strip.width / width);
		int count = perRow * Math.max(1, strip.height / height);
		int frameTime = animation.has("frametime") ? Math.max(1, animation.get("frametime").getAsInt()) : 1;
		interpolate = animation.has("interpolate") && animation.get("interpolate").getAsBoolean();
		List<int[]> order = new ArrayList<>();
		if (animation.get("frames") instanceof com.google.gson.JsonArray list && !list.isEmpty()) {
			for (JsonElement entry : list) {
				if (entry instanceof JsonObject frame) {
					int time = frame.has("time") ? frame.get("time").getAsInt() : frameTime;
					order.add(new int[] {frame.get("index").getAsInt(), time});
				} else {
					order.add(new int[] {entry.getAsInt(), frameTime});
				}
			}
		} else {
			for (int i = 0; i < count; i++) {
				order.add(new int[] {i, frameTime});
			}
		}
		for (int[] frame : order) {
			int index = Math.floorMod(frame[0], count);
			frames.add(strip.crop(index % perRow * width, index / perRow * height, width, height));
			ticks.add(Math.max(1, frame[1]));
		}
	}

	/** Ticks until the animation starts over. */
	int length() {
		return ticks.stream().mapToInt(Integer::intValue).sum();
	}

	boolean interpolates() {
		return interpolate;
	}

	/** The picture at this tick of the loop, blended into the next frame where the game does that. */
	Pixels at(int tick) {
		int time = Math.floorMod(tick, length());
		for (int i = 0; i < frames.size(); i++) {
			int frameTicks = ticks.get(i);
			if (time < frameTicks) {
				Pixels current = frames.get(i);
				if (!interpolate || time == 0) {
					return current;
				}
				return blend(current, frames.get((i + 1) % frames.size()), 1.0 - (double) time / frameTicks);
			}
			time -= frameTicks;
		}
		return frames.get(0);
	}

	/** The game's interpolation: each colour channel mixed by share, the alpha of the current frame kept. */
	private static Pixels blend(Pixels current, Pixels next, double share) {
		Pixels out = new Pixels(current.width, current.height);
		for (int i = 0; i < current.argb.length; i++) {
			int a = current.argb[i];
			int b = next.argb[i];
			int pixel = a & 0xFF000000;
			for (int shift = 16; shift >= 0; shift -= 8) {
				pixel |= (int) (share * (a >> shift & 255) + (1.0 - share) * (b >> shift & 255)) << shift;
			}
			out.argb[i] = pixel;
		}
		return out;
	}

	/** The first frame: what a still picture of this texture shows. */
	Pixels first() {
		return frames.get(0);
	}
}
