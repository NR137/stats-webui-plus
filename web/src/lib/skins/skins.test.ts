import { describe, expect, it } from 'vitest';
import { fittedZoom, isPose, nextEquipment, nextPose } from './poses';
import { patchShaders } from './shaders';

const tan35 = Math.tan((35 * Math.PI) / 180);

describe('fittedZoom', () => {
	it('fits the height on a tall canvas', () => {
		expect(fittedZoom(224, 320, 'walk')).toBeCloseTo(32 / 0.7 / 2 / tan35);
	});

	it('fits the width on a narrow canvas and for flying', () => {
		expect(fittedZoom(100, 320, 'walk')).toBeCloseTo((18 * 3.2) / 0.7 / 2 / tan35);
		expect(fittedZoom(224, 320, 'fly')).toBeCloseTo(((34 * 320) / 224) / 0.7 / 2 / tan35);
	});

	it('treats a zero width as 1', () => {
		expect(fittedZoom(0, 10, 'walk')).toBeCloseTo(180 / 0.7 / 2 / tan35);
	});
});

describe('cycles', () => {
	it('goes through the poses and equipment', () => {
		expect(['idle', 'walk', 'run', 'fly'].map((pose) => nextPose(pose as 'idle'))).toEqual([
			'walk',
			'run',
			'fly',
			'idle'
		]);
		expect(nextEquipment('cape')).toBe('elytra');
		expect(nextEquipment('elytra')).toBe('none');
		expect(nextEquipment('none')).toBe('cape');
		expect(isPose('run')).toBe(true);
		expect(isPose('sit')).toBe(false);
	});
});

describe('patchShaders', () => {
	it('uses centroid and the nearest alpha', () => {
		const { shaders, patched } = patchShaders({
			vertex: 'out vec2 v_uv;\nout vec3 n;',
			fragment: 'in vec2 v_uv;\nif (a) { texColor.a = 1.0; } else { texColor.a = 1.0; }'
		});
		expect(patched).toBe(true);
		expect(shaders.vertex).toBe('centroid out vec2 v_uv;\nout vec3 n;');
		expect(shaders.fragment).toBe(
			'centroid in vec2 v_uv;\nif (a) { texColor.a = nearestAlpha; } else { texColor.a = 1.0; }'
		);
	});

	it('reports a changed shader', () => {
		expect(patchShaders({ vertex: 'x', fragment: 'in vec2 v_uv;' }).patched).toBe(false);
	});
});
