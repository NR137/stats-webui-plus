import { afterEach, describe, expect, it } from 'vitest';
import { i18n } from '#lib/i18n/i18n.svelte.ts';
import { formatValue } from './format';

afterEach(() => {
	i18n.locale = 'en';
});

// In English exactly the texts servers before API version 2 sent preformatted.
describe('formatValue in English', () => {
	it.each([
		[268, 'hours', '268.00 h'],
		[1181.4, 'km', '1181.40 km'],
		[123456.78, 'km', '123456.78 km'],
		[95.5, 'hearts', '95.5 ♥'],
		[12.34, 'kills_per_hour', '12.3/h'],
		[0.12, 'deaths_per_hour', '0.12/h'],
		[3.5, 'ratio', '3.50'],
		[4.41, 'km_per_hour', '4.41 km/h'],
		[4604, 'actions_per_hour', '4,604/h'],
		[1234567.6, 'actions_per_hour', '1,234,568/h'],
		[69.99, 'level', '69'],
		[60.5, 'skill', '61'],
		[60.4, 'skill', '60'],
		[7036, 'count', '7036'],
		[12345, 'kills', '12345'],
		[12.6, 'blocks', '13'],
		[42, 'mob_types', '42'],
		[3, 'something_new', '3'],
		[0, 'hours', '0.00 h'],
		[0, 'count', '0']
	])('%s %s is %s', (value, unit, text) => {
		expect(formatValue(value, unit)).toBe(text);
	});
});

describe('formatValue in other languages', () => {
	it('uses their decimal and thousands separators', () => {
		i18n.locale = 'de';
		expect(formatValue(268, 'hours')).toBe('268,00 h');
		expect(formatValue(4604, 'actions_per_hour')).toBe('4.604/h');
		expect(formatValue(95.5, 'hearts')).toBe('95,5 ♥');
		// Thousands separators only where English has them.
		expect(formatValue(1181.4, 'km')).toBe('1181,40 km');
		expect(formatValue(7036, 'count')).toBe('7036');
	});

	it('follows a language switch', () => {
		expect(formatValue(3.5, 'ratio')).toBe('3.50');
		i18n.locale = 'fr';
		expect(formatValue(3.5, 'ratio')).toBe('3,50');
		i18n.locale = 'en';
		expect(formatValue(3.5, 'ratio')).toBe('3.50');
	});
});
