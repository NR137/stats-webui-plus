import type { ShaderSources } from '@daidr/minecraft-skin-renderer';

export const SHADER_WARNING = 'Skin shader changed: stray pixels may show beside heads and arms';

/**
 * With MSAA the GPU colours a pixel on a face's edge from the texture at the pixel's centre, which may lie outside the
 * face, where the skin holds a neighbouring area (the opaque head next to the hat). The hat and jacket boxes are a
 * little larger than the body, so their transparent faces showed stray strokes beside heads and arms. "centroid" takes
 * the texture position from inside the face.
 */
export function patchShaders(shaders: ShaderSources): { shaders: ShaderSources; patched: boolean } {
	const vertexFrom = 'out vec2 v_uv;';
	const fragmentFrom = 'in vec2 v_uv;';
	const patched = shaders.vertex.includes(vertexFrom) && shaders.fragment.includes(fragmentFrom);
	return {
		shaders: {
			vertex: shaders.vertex.replace(vertexFrom, 'centroid out vec2 v_uv;'),
			fragment: shaders.fragment
				.replace(fragmentFrom, 'centroid in vec2 v_uv;')
				.replace('texColor.a = 1.0;', 'texColor.a = nearestAlpha;')
		},
		patched
	};
}
