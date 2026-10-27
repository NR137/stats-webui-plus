import { describe, expect, it } from 'vitest';
import { de } from './messages/de';
import { en } from './messages/en';
import { es } from './messages/es';
import { fr } from './messages/fr';

const locales: Record<string, Record<string, string>> = { de, en, es, fr };
const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();

describe('translations', () => {
	it.each(Object.keys(locales))('%s has every key and no empty text', (name) => {
		const messages = locales[name];
		expect(Object.keys(messages).sort()).toEqual(Object.keys(es).sort());
		for (const [key, text] of Object.entries(messages)) {
			expect(text.trim(), `${name} ${key}`).not.toBe('');
		}
	});

	it.each(Object.keys(locales))('%s keeps the same {placeholders} as Spanish', (name) => {
		for (const [key, text] of Object.entries(locales[name])) {
			expect(placeholders(text), `${name} ${key}`).toEqual(placeholders((es as Record<string, string>)[key]));
		}
	});
});
