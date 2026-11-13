import { describe, expect, it } from 'vitest';
import { flatten } from './flatten';
import { de } from './messages/de';
import { en } from './messages/en';
import { es } from './messages/es';
import { fr } from './messages/fr';

const locales: Record<string, Record<string, string>> = {
	de: flatten(de),
	en: flatten(en),
	es: flatten(es),
	fr: flatten(fr)
};
const reference = locales.en;
const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();

describe('translations', () => {
	it.each(Object.keys(locales))('%s has every key and no empty text', (name) => {
		const messages = locales[name];
		expect(Object.keys(messages).sort()).toEqual(Object.keys(reference).sort());
		for (const [key, text] of Object.entries(messages)) {
			expect(text.trim(), `${name} ${key}`).not.toBe('');
		}
	});

	it.each(Object.keys(locales))('%s keeps the same {placeholders} as English', (name) => {
		for (const [key, text] of Object.entries(locales[name])) {
			expect(placeholders(text), `${name} ${key}`).toEqual(placeholders(reference[key]));
		}
	});
});
