package de.nr137.statswebuiplus.assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Icons at the sizes the web UI shows them, so the browser shows them as drawn instead of scaling them (which doubles
 * some pixel columns unevenly): &lt;folder&gt;/&lt;size&gt;/&lt;file&gt; next to each icon, for the UI's 24 and 36 pixel boxes on
 * screens with 1, 2 and 3 times the pixels. Still icons get them on request; animated ones (every frame scaled alike)
 * and mob faces (from a finer drawing) when they are made.
 */
final class Sizes {
	static final int[] UI = {24, 36, 48, 72, 108};
	/** The folders of icons the mod makes (faces get their sizes from MobFaces, from a finer drawing). */
	static final List<String> FOLDERS = List.of("item", "block", "iso", "layered", "tinted", "special", "scene");

	private Sizes() {
	}

	/** Where an icon's variant of this size goes: iso/stone.png -> iso/36/stone.png. */
	static Path of(Path icon, int size) {
		return icon.getParent().resolve(Integer.toString(size)).resolve(icon.getFileName());
	}

	/**
	 * A still icon's variant, made when the web UI first asks for it (/mc/item/36/apple.png from item/apple.png): only
	 * what's viewed gets made, and every icon has all sizes. Null if the path isn't a variant or has no icon.
	 */
	static byte[] onRequest(String relative, java.util.function.Function<String, Path> icons) throws IOException {
		java.util.regex.Matcher match = VARIANT.matcher(relative);
		if (!match.matches() || !FOLDERS.contains(match.group(1))) {
			return null;
		}
		int size = Integer.parseInt(match.group(2));
		if (java.util.Arrays.stream(UI).noneMatch(each -> each == size)) {
			return null;
		}
		Path icon = icons.apply(match.group(1) + "/" + match.group(3));
		if (icon == null) {
			return null;
		}
		byte[] png = Files.readAllBytes(icon);
		// Animated icons have their sizes made with their frames; an older cache without them shows the plain one.
		if (!Apng.chunks(png, "acTL").isEmpty()) {
			return null;
		}
		return box(Pixels.decode(png), size).png();
	}

	private static final java.util.regex.Pattern VARIANT = java.util.regex.Pattern.compile("([a-z]+)/(\\d+)/([^/]+\\.png)");

	/** The variants of an animated icon: every frame scaled alike, with the same timing. */
	static void animated(Path icon, List<Apng.Frame> frames) throws IOException {
		for (int size : UI) {
			List<Apng.Frame> scaled = new ArrayList<>();
			for (Apng.Frame frame : frames) {
				scaled.add(new Apng.Frame(box(frame.pixels(), size), frame.millis()));
			}
			Apng.write(of(icon, size), scaled);
		}
	}

	/**
	 * A picture scaled into a size x size box (the longer side fills it), centred: each source pixel becomes a block,
	 * the blocks' edges at round(k * new / old) with halves rounded towards the middle, so the extra pixels are spread
	 * alike on both sides and a symmetric picture stays symmetric.
	 */
	static Pixels box(Pixels picture, int size) {
		double factor = (double) size / Math.max(picture.width, picture.height);
		int width = Math.min(size, (int) Math.round(picture.width * factor));
		int height = Math.min(size, (int) Math.round(picture.height * factor));
		int[] columns = edges(picture.width, width);
		int[] rows = edges(picture.height, height);
		Pixels out = new Pixels(size, size);
		int left = (size - width) / 2;
		int top = (size - height) / 2;
		for (int y = 0; y < picture.height; y++) {
			for (int x = 0; x < picture.width; x++) {
				int pixel = picture.get(x, y);
				for (int py = rows[y]; py < rows[y + 1]; py++) {
					for (int px = columns[x]; px < columns[x + 1]; px++) {
						out.set(left + px, top + py, pixel);
					}
				}
			}
		}
		return out;
	}

	/** Block edges for count source pixels over total pixels, mirror-symmetric around the middle. */
	static int[] edges(int count, int total) {
		int[] edges = new int[count + 1];
		for (int k = 0; k <= count; k++) {
			double exact = (double) k * total / count;
			// Halves towards the middle: in the left half up, in the right half down, so both halves mirror each other.
			edges[k] = 2 * k < count ? (int) Math.ceil(exact - 0.5) : 2 * k > count ? (int) Math.floor(exact + 0.5)
				: (int) Math.round(exact);
		}
		return edges;
	}

}
