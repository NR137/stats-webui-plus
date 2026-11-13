package de.nr137.statswebuiplus.assets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Animated PNG (APNG): a normal .png that browsers play in a plain &lt;img&gt;, with full transparency; one that
 * can't shows the first frame. Each frame is encoded by ImageIO as a PNG of its own, whose image data becomes the
 * frame's data in the animation.
 */
final class Apng {
	/** A frame and how long it shows. */
	record Frame(Pixels pixels, int millis) {
	}

	private Apng() {
	}

	/** Writes the frames as an endless animation; one frame is written as a plain PNG. */
	static void write(Path file, List<Frame> frames) throws IOException {
		List<Frame> merged = merge(frames);
		Files.createDirectories(file.getParent());
		if (merged.size() == 1) {
			merged.get(0).pixels().write(file);
			return;
		}
		int width = merged.get(0).pixels().width;
		int height = merged.get(0).pixels().height;
		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
			byte[] firstPng = png(merged.get(0).pixels());
			chunk(out, "IHDR", chunks(firstPng, "IHDR").get(0));
			chunk(out, "acTL", ByteBuffer.allocate(8).putInt(merged.size()).putInt(0).array());
			int sequence = 0;
			Pixels previous = null;
			for (int i = 0; i < merged.size(); i++) {
				Frame frame = merged.get(i);
				// Only the rectangle that changed since the previous frame; the rest stays (dispose: none). With blend
				// "source" the rectangle replaces what's there, transparent pixels included.
				int[] box = previous == null ? new int[] {0, 0, width, height} : changed(previous, frame.pixels());
				Pixels part = previous == null ? frame.pixels() : frame.pixels().crop(box[0], box[1], box[2], box[3]);
				chunk(out, "fcTL", ByteBuffer.allocate(26)
					.putInt(sequence++).putInt(box[2]).putInt(box[3]).putInt(box[0]).putInt(box[1])
					.putShort((short) Math.min(frame.millis(), 65535)).putShort((short) 1000)
					.put((byte) 0).put((byte) 0).array());
				List<byte[]> data = chunks(i == 0 ? firstPng : png(part), "IDAT");
				for (byte[] piece : data) {
					if (i == 0) {
						chunk(out, "IDAT", piece);
					} else {
						chunk(out, "fdAT", ByteBuffer.allocate(4 + piece.length).putInt(sequence++).put(piece).array());
					}
				}
				previous = frame.pixels();
			}
			chunk(out, "IEND", new byte[0]);
		}
	}

	/** x, y, width, height of the pixels that differ between two frames (at least one pixel). */
	static int[] changed(Pixels before, Pixels after) {
		int left = after.width, top = after.height, right = -1, bottom = -1;
		for (int y = 0; y < after.height; y++) {
			for (int x = 0; x < after.width; x++) {
				if (before.get(x, y) != after.get(x, y)) {
					left = Math.min(left, x);
					right = Math.max(right, x);
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
				}
			}
		}
		return right < 0 ? new int[] {0, 0, 1, 1} : new int[] {left, top, right - left + 1, bottom - top + 1};
	}

	/** Neighbouring frames that look the same become one that shows as long as both. */
	static List<Frame> merge(List<Frame> frames) {
		List<Frame> merged = new ArrayList<>();
		for (Frame frame : frames) {
			Frame last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
			if (last != null && java.util.Arrays.equals(last.pixels().argb, frame.pixels().argb)) {
				merged.set(merged.size() - 1, new Frame(last.pixels(), last.millis() + frame.millis()));
			} else {
				merged.add(frame);
			}
		}
		// A loop whose last frame looks like its first: one frame shows longer instead of two.
		if (merged.size() > 1 && java.util.Arrays.equals(merged.get(0).pixels().argb,
			merged.get(merged.size() - 1).pixels().argb)) {
			Frame last = merged.remove(merged.size() - 1);
			merged.set(0, new Frame(merged.get(0).pixels(), merged.get(0).millis() + last.millis()));
		}
		return merged;
	}

	private static byte[] png(Pixels pixels) throws IOException {
		BufferedImage image = new BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, pixels.width, pixels.height, pixels.argb, 0, pixels.width);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);
		return bytes.toByteArray();
	}

	/** The data of every chunk of this type in a PNG, in order. */
	static List<byte[]> chunks(byte[] png, String type) {
		List<byte[]> found = new ArrayList<>();
		ByteBuffer buffer = ByteBuffer.wrap(png);
		buffer.position(8);
		while (buffer.remaining() >= 12) {
			int length = buffer.getInt();
			byte[] name = new byte[4];
			buffer.get(name);
			byte[] data = new byte[length];
			buffer.get(data);
			buffer.getInt();
			if (new String(name, StandardCharsets.US_ASCII).equals(type)) {
				found.add(data);
			}
		}
		return found;
	}

	private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
		byte[] name = type.getBytes(StandardCharsets.US_ASCII);
		out.write(ByteBuffer.allocate(4).putInt(data.length).array());
		out.write(name);
		out.write(data);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(data);
		out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
	}
}
