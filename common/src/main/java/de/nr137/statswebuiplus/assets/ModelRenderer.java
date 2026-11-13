package de.nr137.statswebuiplus.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.function.Function;

/**
 * Draws a block model (the game's JSON: boxes with a texture per face, possibly rotated) as an inventory icon, in the
 * pixel-sharp iso view of the plain cubes (Pixels.iso): seen from above, the south side on the left, the east side on
 * the right, one texture pixel per icon pixel, a full block filling the 32 x 32 picture. The model's inventory
 * transform ("display": "gui") turns, scales and moves it like the game does, relative to a plain block's.
 *
 * <p>Each picture pixel looks for the faces that land on it (from its centre back along the view), keeps the nearest
 * and takes that face's texture pixel there, shaded by which way the face points: up full bright, towards the left
 * 0.8, towards the right 0.55, as the cubes.
 */
final class ModelRenderer {
	static final int SIZE = 32;

	/** A plain block's inventory view, which the iso picture shows as is. */
	static final double BLOCK_YAW = 225;
	static final double BLOCK_SCALE = 0.625;
	/** Picture pixels per GUI pixel at a plain block's scale (a cube 16 GUI pixels * 0.625 * sqrt 2 wide is 32). */
	static final double GUI_PIXEL = SIZE / (16 * BLOCK_SCALE * Math.sqrt(2));

	/** The face names, their direction (x, y, z) and corners as the game lists them (min or max per axis). */
	private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
	private static final int[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
	/** Per face, its four corners: for each corner 0 = from, 1 = to for x, y, z (net.minecraft's FaceInfo). */
	private static final int[][][] CORNERS = {
		{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
		{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
		{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
		{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
		{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
		{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}};

	/** The inventory transform: turn around the vertical axis (degrees), size and shift (GUI pixels). */
	record View(double yaw, double scale, double right, double up) {
		static final View BLOCK = new View(BLOCK_YAW, BLOCK_SCALE, 0, 0);

		/** From a model's "display": {"gui": {...}}; a plain block's view where it has none. */
		static View of(JsonObject gui) {
			if (gui == null) {
				return BLOCK;
			}
			double yaw = gui.get("rotation") instanceof JsonArray rotation ? rotation.get(1).getAsDouble() : 0;
			double scale = gui.get("scale") instanceof JsonArray size ? size.get(0).getAsDouble() : 1;
			double right = 0;
			double up = 0;
			if (gui.get("translation") instanceof JsonArray shift) {
				right = shift.get(0).getAsDouble();
				up = shift.get(1).getAsDouble();
			}
			return new View(yaw, scale, right, up);
		}
	}

	private final Pixels canvas = new Pixels(SIZE, SIZE);
	/** Which pass: faces looking at the viewer, or the back faces of see-through textures before them. */
	private boolean front = true;
	private final java.util.Map<Pixels, Boolean> seeThrough = new java.util.IdentityHashMap<>();

	/** Whether a texture has half transparent pixels (glass, ice), not only fully clear or solid ones. */
	private boolean seeThrough(Pixels texture) {
		return seeThrough.computeIfAbsent(texture, t -> {
			for (int pixel : t.argb) {
				int alpha = Pixels.alpha(pixel);
				if (alpha > 0 && alpha < 255) {
					return true;
				}
			}
			return false;
		});
	}
	private final double[] depth = new double[SIZE * SIZE];

	private ModelRenderer() {
		java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
	}

	/**
	 * One model of the picture: its elements, textures (texture variable without "#" -> picture; null: that face is
	 * left out), tints (tint index -> colour, or null) and where an item made of several models puts it (in blocks:
	 * moved, scaled, turned by quaternions x, y, z, w, as the item definition's "transformation").
	 */
	record Part(JsonArray elements, Function<String, Pixels> textures, List<Integer> tints, double[] translation,
		double[] scale, double[] leftRotation, double[] rightRotation, List<Face> faces) {
		Part(JsonArray elements, Function<String, Pixels> textures, List<Integer> tints) {
			this(elements, textures, tints, new double[3], new double[] {1, 1, 1}, new double[] {0, 0, 0, 1},
				new double[] {0, 0, 0, 1}, List.of());
		}

		Part(JsonArray elements, Function<String, Pixels> textures, List<Integer> tints, double[] translation,
			double[] scale, double[] leftRotation, double[] rightRotation) {
			this(elements, textures, tints, translation, scale, leftRotation, rightRotation, List.of());
		}

		/** Faces given as they are (an entity model's, see ClientModels.boxes), placed like elements. */
		static Part faces(List<Face> faces, double[] translation, double[] scale, double[] leftRotation, double[] rightRotation) {
			return new Part(new JsonArray(), name -> null, List.of(), translation, scale, leftRotation, rightRotation, faces);
		}

		/** A point (in model pixels) moved like the item definition says; a direction only turned (and mirrored). */
		double[] apply(double[] point, boolean direction) {
			double[] p = turn(rightRotation, point);
			p = direction ? new double[] {p[0] * Math.signum(scale[0]), p[1] * Math.signum(scale[1]), p[2] * Math.signum(scale[2])}
				: new double[] {p[0] * scale[0], p[1] * scale[1], p[2] * scale[2]};
			p = turn(leftRotation, p);
			return direction ? p : new double[] {p[0] + translation[0] * 16, p[1] + translation[1] * 16, p[2] + translation[2] * 16};
		}

		private static double[] turn(double[] q, double[] v) {
			double x = q[0], y = q[1], z = q[2], w = q[3];
			// v + 2w (q x v) + 2 q x (q x v)
			double cx = y * v[2] - z * v[1];
			double cy = z * v[0] - x * v[2];
			double cz = x * v[1] - y * v[0];
			double ccx = y * cz - z * cy;
			double ccy = z * cx - x * cz;
			double ccz = x * cy - y * cx;
			return new double[] {v[0] + 2 * (w * cx + ccx), v[1] + 2 * (w * cy + ccy), v[2] + 2 * (w * cz + ccz)};
		}
	}

	/**
	 * A face as it is: four corners (model pixels) in order, their texture positions (0 to 16 across the texture),
	 * the way it faces and its texture.
	 */
	record Face(double[][] points, double[][] uv, double[] normal, Pixels texture) {
	}

	/** Draws one model; null if nothing was drawn. */
	static Pixels render(JsonArray elements, Function<String, Pixels> textures, List<Integer> tints, View view) {
		return render(List.of(new Part(elements, textures, tints)), view);
	}

	/** Draws the models together (an item made of several, like a bed's two halves); null if nothing was drawn. */
	static Pixels render(List<Part> parts, View view) {
		ModelRenderer renderer = new ModelRenderer();
		boolean drawn = false;
		// First the back faces of see-through textures (a glass block's inside, seen through its front, as the game
		// draws it), then all faces that look at the viewer.
		for (boolean front : new boolean[] {false, true}) {
			renderer.front = front;
			for (Part part : parts) {
				for (JsonElement entry : part.elements()) {
					drawn |= renderer.element(entry.getAsJsonObject(), part, view);
				}
				for (Face face : part.faces()) {
					double[][] points = new double[4][];
					for (int c = 0; c < 4; c++) {
						points[c] = place(part.apply(face.points()[c], false), view);
					}
					double[] normal = place(part.apply(face.normal(), true), view, true);
					drawn |= renderer.quad(points, face.uv(), normal, face.texture(), null, view);
				}
			}
		}
		return drawn ? renderer.canvas : null;
	}

	private boolean element(JsonObject element, Part part, View view) {
		Function<String, Pixels> textures = part.textures();
		List<Integer> tints = part.tints();
		double[] from = vector(element.getAsJsonArray("from"));
		double[] to = vector(element.getAsJsonArray("to"));
		JsonObject faces = element.getAsJsonObject("faces");
		if (faces == null) {
			return false;
		}
		boolean drawn = false;
		for (int f = 0; f < FACES.length; f++) {
			if (!(faces.get(FACES[f]) instanceof JsonObject face)) {
				continue;
			}
			String name = face.has("texture") ? face.get("texture").getAsString() : null;
			Pixels texture = name == null ? null : textures.apply(name.startsWith("#") ? name.substring(1) : name);
			if (texture == null) {
				continue;
			}
			Integer tint = null;
			if (face.has("tintindex")) {
				int index = face.get("tintindex").getAsInt();
				tint = index >= 0 && index < tints.size() ? tints.get(index) : null;
			}
			double[][] points = new double[4][];
			for (int c = 0; c < 4; c++) {
				int[] corner = CORNERS[f][c];
				points[c] = new double[] {corner[0] == 0 ? from[0] : to[0], corner[1] == 0 ? from[1] : to[1],
					corner[2] == 0 ? from[2] : to[2]};
				points[c] = place(part.apply(rotate(points[c], element.getAsJsonObject("rotation")), false), view);
			}
			double[] normal = place(part.apply(rotate(new double[] {NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]},
				element.getAsJsonObject("rotation"), true), true), view, true);
			double[][] uv = uv(face, f, from, to);
			drawn |= quad(points, uv, normal, texture, tint, view);
		}
		return drawn;
	}

	/** The face's texture corners for its four corners, the face's own "rotation" applied (the game's default uv). */
	private static double[][] uv(JsonObject face, int f, double[] from, double[] to) {
		double[] box;
		if (face.get("uv") instanceof JsonArray uv) {
			box = new double[] {uv.get(0).getAsDouble(), uv.get(1).getAsDouble(), uv.get(2).getAsDouble(), uv.get(3).getAsDouble()};
		} else {
			box = switch (FACES[f]) {
				case "down" -> new double[] {from[0], 16 - to[2], to[0], 16 - from[2]};
				case "up" -> new double[] {from[0], from[2], to[0], to[2]};
				case "north" -> new double[] {16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
				case "south" -> new double[] {from[0], 16 - to[1], to[0], 16 - from[1]};
				case "west" -> new double[] {from[2], 16 - to[1], to[2], 16 - from[1]};
				default -> new double[] {16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
			};
		}
		double[][] corners = {{box[0], box[1]}, {box[0], box[3]}, {box[2], box[3]}, {box[2], box[1]}};
		int turn = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt() / 90, 4) : 0;
		double[][] result = new double[4][];
		for (int c = 0; c < 4; c++) {
			result[c] = corners[(c + turn) % 4];
		}
		return result;
	}

	private static double[] vector(JsonArray array) {
		return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
	}

	private static double[] rotate(double[] point, JsonObject rotation) {
		return rotate(point, rotation, false);
	}

	/** An element's own rotation (around its origin, rescaled if it says so); a direction only turns. */
	private static double[] rotate(double[] point, JsonObject rotation, boolean direction) {
		if (rotation == null || !rotation.has("angle")) {
			return point;
		}
		double angle = Math.toRadians(rotation.get("angle").getAsDouble());
		if (angle == 0) {
			return point;
		}
		int axis = switch (rotation.get("axis").getAsString()) {
			case "x" -> 0;
			case "y" -> 1;
			default -> 2;
		};
		double[] origin = direction ? new double[3] : vector(rotation.getAsJsonArray("origin"));
		double[] p = {point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]};
		int a = (axis + 1) % 3;
		int b = (axis + 2) % 3;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		// The game turns x around y as (x cos + z sin, -x sin + z cos); around x and z the same pattern.
		double pa = axis == 1 ? p[b] : p[a];
		double pb = axis == 1 ? p[a] : p[b];
		double na = pa * cos - pb * sin;
		double nb = pa * sin + pb * cos;
		if (axis == 1) {
			p[b] = na;
			p[a] = nb;
		} else {
			p[a] = na;
			p[b] = nb;
		}
		if (!direction && rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
			double factor = 1 / Math.cos(Math.abs(angle) > Math.PI / 4 ? Math.PI / 2 - Math.abs(angle) : Math.abs(angle));
			p[a] *= factor;
			p[b] *= factor;
		}
		return new double[] {p[0] + origin[0], p[1] + origin[1], p[2] + origin[2]};
	}

	private static double[] place(double[] point, View view) {
		return place(point, view, false);
	}

	/** The inventory transform relative to a plain block's: turned around the block's centre, scaled, shifted. */
	private static double[] place(double[] point, View view, boolean direction) {
		// The game shows a plain block (turned 225 degrees) with its east side on the left and its north side on the
		// right; the iso picture shows +z on the left and +x on the right, a further quarter turn.
		double angle = Math.toRadians(view.yaw() - BLOCK_YAW - 90);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double centre = direction ? 0 : 8;
		double x = point[0] - centre;
		double z = point[2] - centre;
		double tx = x * cos + z * sin;
		double tz = -x * sin + z * cos;
		if (direction) {
			return new double[] {tx, point[1], tz};
		}
		double factor = view.scale() / BLOCK_SCALE;
		return new double[] {tx * factor + 8, (point[1] - 8) * factor + 8, tz * factor + 8};
	}

	/** Where a model point lands in the picture (x, y, moved by the transform's shift) and how near it is (larger: nearer). */
	private static double[] project(double[] p, View view) {
		return new double[] {p[0] - p[2] + 16 + view.right() * GUI_PIXEL,
			(p[0] + p[2]) / 2 - p[1] + 16 - view.up() * GUI_PIXEL, p[0] + p[1] + p[2]};
	}

	private boolean quad(double[][] points, double[][] uv, double[] normal, Pixels texture, Integer tint, View view) {
		// Faces pointing away from the viewer (who looks along -1, -1, -1) are only seen through see-through ones.
		boolean facing = normal[0] + normal[1] + normal[2] > 1e-6;
		if (front ? !facing : facing || !seeThrough(texture)) {
			return false;
		}
		// A back face is shaded the way it points (the bottom seen through the top: darker), as the game does.
		double shade = shade(normal);
		double[] origin = project(points[0], view);
		double[] edgeA = minus(project(points[1], view), origin);
		double[] edgeB = minus(project(points[3], view), origin);
		double det = edgeA[0] * edgeB[1] - edgeA[1] * edgeB[0];
		if (Math.abs(det) < 1e-9) {
			return false;
		}
		// A block texture taller than wide is an animation's strip of frames: its first frame. Entity textures may be
		// wider than tall (a skull's 64 x 32).
		int across = texture.width;
		int down = texture.height > texture.width ? texture.width : texture.height;
		boolean drawn = false;
		double minX = Math.min(Math.min(origin[0], origin[0] + edgeA[0]), Math.min(origin[0] + edgeB[0], origin[0] + edgeA[0] + edgeB[0]));
		double maxX = Math.max(Math.max(origin[0], origin[0] + edgeA[0]), Math.max(origin[0] + edgeB[0], origin[0] + edgeA[0] + edgeB[0]));
		double minY = Math.min(Math.min(origin[1], origin[1] + edgeA[1]), Math.min(origin[1] + edgeB[1], origin[1] + edgeA[1] + edgeB[1]));
		double maxY = Math.max(Math.max(origin[1], origin[1] + edgeA[1]), Math.max(origin[1] + edgeB[1], origin[1] + edgeA[1] + edgeB[1]));
		for (int y = Math.max(0, (int) Math.floor(minY)); y < Math.min(SIZE, (int) Math.ceil(maxY) + 1); y++) {
			for (int x = Math.max(0, (int) Math.floor(minX)); x < Math.min(SIZE, (int) Math.ceil(maxX) + 1); x++) {
				double px = x + 0.5 - origin[0];
				double py = y + 0.5 - origin[1];
				double a = (px * edgeB[1] - py * edgeB[0]) / det;
				double b = (edgeA[0] * py - edgeA[1] * px) / det;
				if (a < -1e-9 || a >= 1 - 1e-9 || b < -1e-9 || b >= 1 - 1e-9) {
					continue;
				}
				double z = origin[2] + a * edgeA[2] + b * edgeB[2];
				int index = y * SIZE + x;
				if (z <= depth[index] + 1e-6) {
					continue;
				}
				double u = uv[0][0] + a * (uv[1][0] - uv[0][0]) + b * (uv[3][0] - uv[0][0]);
				double v = uv[0][1] + a * (uv[1][1] - uv[0][1]) + b * (uv[3][1] - uv[0][1]);
				int tx = Math.min(across - 1, Math.max(0, (int) Math.floor(u * across / 16)));
				int ty = Math.min(down - 1, Math.max(0, (int) Math.floor(v * down / 16)));
				int pixel = texture.get(tx, ty);
				if (Pixels.alpha(pixel) == 0) {
					continue;
				}
				if (tint != null) {
					pixel = tinted(pixel, tint);
				}
				if (Pixels.alpha(pixel) == 255) {
					canvas.set(x, y, Pixels.shade(pixel, shade));
					depth[index] = z;
				} else {
					// See-through (stained glass, frosted ice): mixed over what's there, which stays visible behind.
					canvas.set(x, y, Scenes.over(canvas.get(x, y), Pixels.shade(pixel, shade)));
				}
				drawn = true;
			}
		}
		return drawn;
	}

	/** Brightness by direction: up 1, towards the left (south in the picture) 0.8, towards the right 0.55. */
	private static double shade(double[] normal) {
		double length = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
		double x = normal[0] / length;
		double y = normal[1] / length;
		double z = normal[2] / length;
		return y * y * (y > 0 ? 1.0 : 0.5) + z * z * 0.8 + x * x * 0.55;
	}

	private static int tinted(int pixel, int rgb) {
		return pixel & 0xFF000000 | ((pixel >> 16 & 255) * (rgb >> 16 & 255) / 255) << 16
			| ((pixel >> 8 & 255) * (rgb >> 8 & 255) / 255) << 8 | (pixel & 255) * (rgb & 255) / 255;
	}

	private static double[] minus(double[] a, double[] b) {
		return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}
}
