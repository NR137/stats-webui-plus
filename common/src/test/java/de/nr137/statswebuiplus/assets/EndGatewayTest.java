package de.nr137.statswebuiplus.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The end gateway's icon: the game's portal shader, as a seamless loop. */
class EndGatewayTest {
	@Test
	void aSeamlessLoopOfTenPicturesASecond() {
		Random random = new Random(1);
		Pixels sky = new Pixels(16, 16);
		Pixels stars = new Pixels(64, 64);
		for (int i = 0; i < sky.argb.length; i++) {
			sky.argb[i] = 0xFF000000 | random.nextInt(0x40) * 0x010101;
		}
		for (int i = 0; i < stars.argb.length; i++) {
			stars.argb[i] = random.nextInt(20) == 0 ? 0xFFFFFFFF : 0xFF000000;
		}
		List<Apng.Frame> frames = EndGateway.frames(sky, stars);
		assertEquals(EndGateway.TICKS / EndGateway.STEP, frames.size());
		assertTrue(frames.stream().allMatch(frame -> frame.millis() == EndGateway.STEP * AnimatedTexture.TICK));
		Pixels first = frames.get(0).pixels();
		assertEquals(0, Pixels.alpha(first.get(0, 0)), "only the block's outline is filled");
		assertEquals(255, Pixels.alpha(first.get(16, 16)));
		// From the last picture back to the first no bigger step than between two neighbouring ones.
		long typical = difference(frames.get(10).pixels(), frames.get(11).pixels());
		long seam = difference(frames.get(frames.size() - 1).pixels(), first);
		assertTrue(seam <= 2 * typical, "seam " + seam + ", typical step " + typical);
	}

	private static long difference(Pixels a, Pixels b) {
		long sum = 0;
		for (int i = 0; i < a.argb.length; i++) {
			for (int shift = 0; shift < 24; shift += 8) {
				sum += Math.abs((a.argb[i] >> shift & 255) - (b.argb[i] >> shift & 255));
			}
		}
		return sum;
	}
}
