import { i18n } from '#lib/i18n/i18n.svelte.ts';

// The API's values are plain numbers with a unit; this is how the website shows them, in the visitor's language.
// In English the text is exactly what servers before API version 2 sent preformatted ("268.00 h", "4,604/h", "69"),
// so the pages look as they did.

type Style = {
	decimals: number;
	// Thousands separators: only where the old texts had them.
	grouping?: boolean;
	suffix?: string;
	// The level is a whole number like the game's experience level: rounded down, never up.
	floor?: boolean;
};

const styles: Record<string, Style> = {
	hours: { decimals: 2, suffix: ' h' },
	km: { decimals: 2, suffix: ' km' },
	hearts: { decimals: 1, suffix: ' ♥' },
	kills_per_hour: { decimals: 1, suffix: '/h' },
	deaths_per_hour: { decimals: 2, suffix: '/h' },
	ratio: { decimals: 2 },
	km_per_hour: { decimals: 2, suffix: ' km/h' },
	actions_per_hour: { decimals: 0, grouping: true, suffix: '/h' },
	level: { decimals: 0, floor: true }
};

// Counts, kills, blocks, mob types, a skill's value (`skill`, called `skill` before) and anything new.
const whole: Style = { decimals: 0 };

const formats = new Map<string, Intl.NumberFormat>();

function numberFormat(locale: string, decimals: number, grouping: boolean): Intl.NumberFormat {
	const key = `${locale}/${decimals}/${grouping}`;
	let format = formats.get(key);
	if (!format) {
		format = new Intl.NumberFormat(locale, {
			minimumFractionDigits: decimals,
			maximumFractionDigits: decimals,
			useGrouping: grouping
		});
		formats.set(key, format);
	}
	return format;
}

/** A value of the API in its unit, as text in the current language: "268.00 h", German "268,00 h". */
export function formatValue(value: number, unit: string): string {
	// Read here, not cached outside, so a template showing this updates when the language changes.
	const locale = i18n.locale;
	const style = styles[unit] ?? whole;
	const number = style.floor ? Math.floor(value) : value;
	return numberFormat(locale, style.decimals, style.grouping ?? false).format(number) + (style.suffix ?? '');
}
