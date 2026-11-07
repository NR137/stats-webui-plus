import type { CustomPageResponse } from '$lib/types';

// Turns a server owner's page (config pages) into HTML: Markdown with marked, HTML as written, text as paragraphs.
// Whatever comes out goes through DOMPurify, so a page can't run scripts or break the site around it. Both libraries
// are loaded only here, so other pages don't download them.

export type RenderedPage = {
	// The text of a leading top heading ("# Imprint"), which then is the page's title instead of the label.
	title: string | null;
	html: string;
};

const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escape(text: string): string {
	return text.replace(/[&<>"']/g, (char) => ESCAPES[char]);
}

/** Plain text: paragraphs at blank lines, line breaks kept, nothing read as markup. */
export function textToHtml(text: string): string {
	return text
		.replace(/\r\n?/g, '\n')
		.split(/\n[ \t]*\n/)
		.map((paragraph) => paragraph.replace(/^\n+|\s+$/g, ''))
		.filter((paragraph) => paragraph.trim() !== '')
		.map((paragraph) => `<p>${escape(paragraph).replaceAll('\n', '<br>')}</p>`)
		.join('\n');
}

export async function renderCustomPage(page: Pick<CustomPageResponse, 'format' | 'content'>): Promise<RenderedPage> {
	const { default: DOMPurify } = await import('dompurify');
	let html: string;
	if (page.format === 'markdown') {
		const { marked } = await import('marked');
		html = await marked.parse(page.content, { gfm: true });
	} else if (page.format === 'html') {
		html = page.content;
	} else {
		html = textToHtml(page.content);
	}
	const fragment = DOMPurify.sanitize(html, {
		RETURN_DOM_FRAGMENT: true,
		// Styles would apply to the whole site, not just this page (style attributes stay); forms can't do anything
		// without scripts. Frames (iframe, embed, object) DOMPurify removes anyway.
		FORBID_TAGS: ['style', 'form', 'input', 'button', 'select', 'textarea'],
		ADD_ATTR: ['target']
	});
	// Links that open a new tab can't reach back into this one.
	for (const link of fragment.querySelectorAll('a[target]')) {
		link.setAttribute('rel', 'noopener noreferrer');
	}
	let title: string | null = null;
	const first = fragment.firstElementChild;
	const before = first ? [...fragment.childNodes].slice(0, [...fragment.childNodes].indexOf(first)) : [];
	const leading = before.every((node) => !node.textContent?.trim());
	if (first?.tagName === 'H1' && leading && first.textContent?.trim()) {
		title = first.textContent.trim();
		first.remove();
	}
	const holder = document.createElement('div');
	holder.append(fragment);
	return { title, html: holder.innerHTML };
}
