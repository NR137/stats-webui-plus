package de.nr137.statswebuiplus.assets.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs inside ClientClasses' class loader, next to the downloaded client JAR, so it can use the game's client-only
 * model code (a server has none of it); everything else comes from the running server. Reads the head of each wanted
 * entity model layer, seen from the front and from the side, for MobFaces. Only JSON text leaves this class.
 *
 * Each quad: the part's path ("/head/nose"), then per corner screen x, y, depth (smaller is nearer), u, v (fractions of
 * the texture).
 */
public final class ClientModels {
	/**
	 * Models the game draws with back faces (render type without culling), where they show: an enderman's dark head
	 * inside through the see-through lower rows of its face.
	 */
	private static final Set<String> TWO_SIDED = Set.of("enderman#main");

	/** Horses, whose model holds head and neck tilted forward: upright, as their icons show them. */
	private static final Set<String> UPRIGHT_NECKS = Set.of("horse#main", "donkey#main", "mule#main", "skeleton_horse#main",
		"zombie_horse#main");

	private ClientModels() {
	}

	/** Poses for the icons where the model's resting pose hides what makes the mob recognisable. */
	private static void pose(String name, ModelPart root) {
		if (UPRIGHT_NECKS.contains(name) && root.hasChild("head_parts")) {
			root.getChild("head_parts").xRot = 0;
		}
		// A shulker peeking out, as its pictures show it: lid raised a little, the head sitting on the base and showing
		// through the cut-out at the bottom of the lid's front.
		if (name.equals("shulker#main") && root.hasChild("lid") && root.hasChild("head")) {
			root.getChild("lid").y -= 6;
			root.getChild("head").y -= 2;
		}
		// A camel's ears lie flat at the top of its head, where its outline hides them from the side: a pixel higher.
		if (name.equals("camel#main") && root.hasChild("body") && root.getChild("body").hasChild("head")) {
			ModelPart head = root.getChild("body").getChild("head");
			for (String ear : new String[] {"left_ear", "right_ear"}) {
				if (head.hasChild(ear)) {
					head.getChild(ear).y -= 1;
				}
			}
		}
	}

	/** The wanted layers ("cow#main") as JSON: {layer: {front: [quads], side: [quads]}}. */
	public static String json(Set<String> wanted) {
		Map<ModelLayerLocation, LayerDefinition> roots = LayerDefinitions.createRoots();
		Map<String, Object> models = new TreeMap<>();
		for (Map.Entry<ModelLayerLocation, LayerDefinition> entry : roots.entrySet()) {
			String name = entry.getKey().model().getPath() + "#" + entry.getKey().layer();
			if (!wanted.contains(name)) {
				continue;
			}
			ModelPart root = entry.getValue().bakeRoot();
			pose(name, root);
			Map<String, Object> model = new LinkedHashMap<>();
			boolean twoSided = TWO_SIDED.contains(name);
			model.put("front", quads(root, false, twoSided));
			model.put("side", quads(root, true, twoSided));
			models.put(name, model);
		}
		return new GsonBuilder().create().toJson(models);
	}

	/**
	 * The quads facing the viewer, from the front (looking at the face, -Z) or from the side (the mob's left, +X, its
	 * head on the left). Each: the part's path ("/head/nose"), then per corner screen x, y, depth (smaller is nearer),
	 * u, v.
	 */
	private static List<Object[]> quads(ModelPart root, boolean side, boolean twoSided) {
		List<Object[]> quads = new ArrayList<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			for (ModelPart.Polygon polygon : cube.polygons) {
				Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
				// Every face turned towards the viewer, also slanted ones (a goat's lowered head): the depth buffer of
				// MobFaces decides what's in front. Not faces seen almost edge-on (the sides of a slightly turned head):
				// they would be one-pixel strips at the edges. Faces with no area have no direction; NaN fails every comparison.
				// The game draws some models without hiding back faces: a shulker's open shell shows its inside.
				boolean visible = side ? normal.x() > 0.2f : normal.z() < -0.2f;
				boolean inside = twoSided && (side ? normal.x() < -0.2f : normal.z() > 0.2f);
				if (!visible && !inside) {
					continue;
				}
				Object[] quad = new Object[1 + polygon.vertices().length * 5];
				quad[0] = path;
				int i = 1;
				boolean valid = true;
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					Vector3f position = pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
					float x = side ? position.z() : position.x();
					float depth = side ? -position.x() : position.z();
					float[] values = {x * 16, position.y() * 16, depth * 16, vertex.u(), vertex.v()};
					for (float value : values) {
						valid &= Float.isFinite(value);
						quad[i++] = round(value);
					}
				}
				if (valid) {
					quads.add(quad);
				}
			}
		});
		return quads;
	}

	/**
	 * Every face of the wanted layers in 3D, in the model's own pixels, with no pose changed: for block entities
	 * (chests, shulker boxes, heads, banners) that ModelRenderer draws as inventory icons. {layer: [quads]}; each quad:
	 * the part's path, then per corner x, y, z, u, v (u, v fractions of the texture), then the direction x, y, z.
	 */
	public static String boxes(Set<String> wanted) {
		Map<ModelLayerLocation, LayerDefinition> roots = LayerDefinitions.createRoots();
		Map<String, Object> models = new TreeMap<>();
		for (Map.Entry<ModelLayerLocation, LayerDefinition> entry : roots.entrySet()) {
			String name = entry.getKey().model().getPath() + "#" + entry.getKey().layer();
			if (!wanted.contains(name)) {
				continue;
			}
			List<Object[]> quads = new ArrayList<>();
			entry.getValue().bakeRoot().visit(new PoseStack(), (pose, path, index, cube) -> {
				for (ModelPart.Polygon polygon : cube.polygons) {
					Object[] quad = new Object[1 + polygon.vertices().length * 5 + 3];
					quad[0] = path;
					int i = 1;
					boolean valid = true;
					for (ModelPart.Vertex vertex : polygon.vertices()) {
						Vector3f position = pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
						float[] values = {position.x() * 16, position.y() * 16, position.z() * 16, vertex.u(), vertex.v()};
						for (float value : values) {
							valid &= Float.isFinite(value);
							quad[i++] = round(value);
						}
					}
					Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
					for (float value : new float[] {normal.x(), normal.y(), normal.z()}) {
						valid &= Float.isFinite(value);
						quad[i++] = round(value);
					}
					if (valid) {
						quads.add(quad);
					}
				}
			});
			models.put(name, quads);
		}
		return new GsonBuilder().create().toJson(models);
	}

	/** The names of all model layers ("chest#main"), to find the ones to ask for. */
	public static String layers() {
		return new GsonBuilder().create().toJson(LayerDefinitions.createRoots().keySet().stream()
			.map(location -> location.model().getPath() + "#" + location.layer()).sorted().toList());
	}

	private static float round(float value) {
		return Math.round(value * 1000) / 1000f;
	}

}
