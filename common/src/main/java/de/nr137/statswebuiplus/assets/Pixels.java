package de.nr137.statswebuiplus.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An image as straight (not premultiplied) ARGB pixels, and the few drawing steps the icons need. Pixel for pixel
 * the same arithmetic as the game's look needs: whole numbers cut off, never smoothed.
 */
final class Pixels {
	final int width;
	final int height;
	final int[] argb;

	Pixels(int width, int height) {
		this(width, height, new int[width * height]);
	}

	private Pixels(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	int get(int x, int y) {
		return argb[y * width + x];
	}

	void set(int x, int y, int pixel) {
		argb[y * width + x] = pixel;
	}

	static Pixels read(Path file) throws IOException {
		return decode(Files.readAllBytes(file));
	}

	/**
	 * Decodes a PNG. Grey images are read from their samples: Java's own conversion to RGB would treat their grey as
	 * linear and brighten it, which the game doesn't.
	 */
	static Pixels decode(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		if (image == null) {
			throw new IOException("Not an image");
		}
		int width = image.getWidth();
		int height = image.getHeight();
		Pixels pixels = new Pixels(width, height);
		boolean grey = image.getColorModel().getColorSpace().getNumComponents() == 1
			&& image.getColorModel().getPixelSize() <= 16 && !(image.getColorModel() instanceof java.awt.image.IndexColorModel);
		if (grey) {
			Raster raster = image.getRaster();
			int bands = raster.getNumBands();
			int depth = image.getColorModel().getComponentSize(0);
			int max = (1 << depth) - 1;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int value = raster.getSample(x, y, 0) * 255 / max;
					int alpha = bands > 1 ? raster.getSample(x, y, 1) * 255 / max : 255;
					pixels.set(x, y, alpha << 24 | value << 16 | value << 8 | value);
				}
			}
			if (bands == 1 && image.getColorModel().hasAlpha()) {
				// Grey with a transparent value (tRNS): the colour model knows which.
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int alpha = image.getColorModel().getAlpha(image.getRaster().getDataElements(x, y, null));
						pixels.set(x, y, alpha << 24 | (pixels.get(x, y) & 0xFFFFFF));
					}
				}
			}
			return pixels;
		}
		image.getRGB(0, 0, width, height, pixels.argb, 0, width);
		return pixels;
	}

	/** As PNG bytes. */
	byte[] png() throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, argb, 0, width);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return bytes.toByteArray();
	}

	void write(Path file) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, argb, 0, width);
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "png", file.toFile());
	}

	/** Scaled to the new size, each new pixel taking the source pixel it falls on (nearest neighbour). */
	Pixels scale(int newWidth, int newHeight) {
		Pixels out = new Pixels(newWidth, newHeight);
		if (width == 0 || height == 0) {
			return out;
		}
		int[] columns = new int[newWidth];
		for (int x = 0; x < newWidth; x++) {
			columns[x] = (int) ((double) x * width / newWidth);
		}
		for (int y = 0; y < newHeight; y++) {
			int row = (int) ((double) y * height / newHeight) * width;
			for (int x = 0; x < newWidth; x++) {
				out.argb[y * newWidth + x] = argb[row + columns[x]];
			}
		}
		return out;
	}

	Pixels crop(int x, int y, int cropWidth, int cropHeight) {
		Pixels out = new Pixels(cropWidth, cropHeight);
		for (int row = 0; row < cropHeight; row++) {
			System.arraycopy(argb, (y + row) * width + x, out.argb, row * cropWidth, cropWidth);
		}
		return out;
	}

	/** Multiplies every colour channel by the tint (whole numbers, cut off), as the game tints grass and leaves. */
	Pixels tint(int rgb) {
		int r = rgb >> 16 & 255;
		int g = rgb >> 8 & 255;
		int b = rgb & 255;
		Pixels out = new Pixels(width, height);
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			out.argb[i] = p & 0xFF000000 | ((p >> 16 & 255) * r / 255) << 16 | ((p >> 8 & 255) * g / 255) << 8
				| (p & 255) * b / 255;
		}
		return out;
	}

	/** More transparent: every pixel's alpha times opacity. */
	Pixels fade(float opacity) {
		Pixels out = new Pixels(width, height);
		for (int i = 0; i < argb.length; i++) {
			out.argb[i] = Math.round(alpha(argb[i]) * opacity) << 24 | argb[i] & 0xFFFFFF;
		}
		return out;
	}

	/** Every pixel that isn't fully transparent made fully opaque. */
	Pixels opaque() {
		Pixels out = new Pixels(width, height);
		for (int i = 0; i < argb.length; i++) {
			out.argb[i] = alpha(argb[i]) == 0 ? 0 : argb[i] | 0xFF000000;
		}
		return out;
	}

	static int alpha(int pixel) {
		return pixel >>> 24;
	}

	/** Each colour channel times factor (at most 255), alpha kept: the darker and lighter sides of an iso cube. */
	static int shade(int pixel, double factor) {
		int out = pixel & 0xFF000000;
		for (int shift = 16; shift >= 0; shift -= 8) {
			out |= Math.min(255, (int) ((pixel >> shift & 255) * factor)) << shift;
		}
		return out;
	}

	/**
	 * Draws a pixel over what's there: opaque replaces it, fully transparent leaves it, anything between mixes the
	 * colours by its alpha and adds the alphas up.
	 */
	void put(int x, int y, int pixel) {
		int alpha = alpha(pixel);
		if (alpha == 0 || x < 0 || y < 0 || x >= width || y >= height) {
			return;
		}
		set(x, y, alpha == 255 ? pixel : mix(get(x, y), pixel, alpha));
	}

	private static int mix(int under, int over, int alpha) {
		double weight = alpha / 255.0;
		int out = Math.min(255, alpha + alpha(under)) << 24;
		for (int shift = 16; shift >= 0; shift -= 8) {
			out |= (int) ((over >> shift & 255) * weight + (under >> shift & 255) * (1 - weight)) << shift;
		}
		return out;
	}

	/**
	 * A cube of three faces, each scaled to size x size, on a 32 x 32 canvas as the inventory shows blocks: a full
	 * block at size 16, smaller ones centred. The view is the pixel-art isometric one, two pixels across for one down:
	 * a face pixel's column moves the picture point one right and half a pixel down (left face) or up (right face);
	 * on top, both directions run diagonally. Top lit fully, the left side at 0.8, the right side at 0.55.
	 */
	static Pixels iso(Pixels top, Pixels left, Pixels right, int size) {
		Pixels[] faces = {left.scale(size, size), right.scale(size, size), top.scale(size, size)};
		double[] light = {0.8, 0.55, 1.0};
		Pixels canvas = new Pixels(32, 32);
		int origin = 16 - size;
		int last = size - 1;
		// Left and right in one pass, pixel by pixel (where they meet in the middle column the right one is below),
		// then the top over both.
		for (int pass = 0; pass < 2; pass++) {
			for (int v = 0; v < size; v++) {
				for (int u = 0; u < size; u++) {
					if (pass == 0) {
						canvas.put(origin + u, origin + (u + last) / 2 + v, shade(faces[0].get(u, v), light[0]));
						canvas.put(origin + last + u, origin + (2 * last - u) / 2 + v, shade(faces[1].get(u, v), light[1]));
					} else {
						canvas.put(origin + last + u - v, origin + (u + v) / 2, shade(faces[2].get(u, v), light[2]));
					}
				}
			}
		}
		return canvas;
	}

	/** Layer over this one ("source over"), rounded half to even like the original icons were. */
	void over(Pixels layer) {
		Pixels top = layer.width == width && layer.height == height ? layer : layer.scale(width, height);
		for (int i = 0; i < argb.length; i++) {
			int src = top.argb[i];
			double alpha = alpha(src) / 255.0;
			if (alpha <= 0) {
				continue;
			}
			int dst = argb[i];
			double outAlpha = alpha + alpha(dst) / 255.0 * (1 - alpha);
			int pixel = (int) Math.rint(outAlpha * 255) << 24;
			for (int shift = 16; shift >= 0; shift -= 8) {
				double mixed = ((src >> shift & 255) * alpha + (double) ((dst >> shift & 255) * alpha(dst)) / 255 * (1 - alpha))
					/ outAlpha;
				pixel |= (int) Math.rint(mixed) << shift;
			}
			argb[i] = pixel;
		}
	}
}
