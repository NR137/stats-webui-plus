<script lang="ts">
	// A translated text with links in it: "{font}" in the text becomes the link links.font, in any language's word order.
	// Links open in a new tab (they all lead off the site).
	let { text, links }: { text: string; links: Record<string, { label: string; url: string }> } = $props();

	const parts = $derived(
		text
			.split(/(\{\w+\})/)
			.filter(Boolean)
			.map((part) => links[part.slice(1, -1)] ?? part)
	);
</script>

{#each parts as part}{#if typeof part === 'string'}{part}{:else}<a
			href={part.url}
			target="_blank"
			rel="noopener noreferrer">{part.label}</a
		>{/if}{/each}
