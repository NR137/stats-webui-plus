import { describe, expect, it } from 'vitest';
import { textToHtml } from './customPage';

// README, Custom pages: a .txt file is shown as written: paragraphs at blank lines, line breaks kept, nothing in it
// read as HTML.

describe('textToHtml', () => {
	it('keeps paragraphs and line breaks', () => {
		expect(textToHtml('Alex Example\nMain Street 1\n\nContact: alex@example.com\n')).toBe(
			'<p>Alex Example<br>Main Street 1</p>\n<p>Contact: alex@example.com</p>'
		);
	});

	it('reads Windows line ends and several blank lines as one break', () => {
		expect(textToHtml('One\r\n\r\n\r\nTwo\r\nThree')).toBe('<p>One</p>\n<p>Two<br>Three</p>');
	});

	it('shows markup as text', () => {
		expect(textToHtml('<script>alert(1)</script> & "quotes"')).toBe(
			'<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;quotes&quot;</p>'
		);
	});
});
