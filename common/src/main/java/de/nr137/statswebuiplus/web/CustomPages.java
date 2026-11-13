package de.nr137.statswebuiplus.web;

import de.nr137.statswebuiplus.CustomPage;
import de.nr137.statswebuiplus.StatsWebuiPlus;
import de.nr137.statswebuiplus.WebConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The texts of the configured pages ("pages"): {@code config/stats_webui_plus/pages/<id>/<language>.<md|html|txt>},
 * e.g. {@code pages/imprint/de.md}, or {@code page.<md|html|txt>} for every language. The web UI renders them.
 */
public final class CustomPages {
	public static final String FOLDER = "pages";
	/** The file a page uses for every language without a file of its own. */
	public static final String NEUTRAL = "page";
	/** Bigger files are left out: a page is text, and the whole file goes to every visitor. */
	public static final long MAX_BYTES = 512 * 1024;
	// Tried in this order when one language has several files (en.md and en.html).
	private static final Map<String, String> FORMATS = formats();
	private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2,3}");

	private CustomPages() {
	}

	private static Map<String, String> formats() {
		Map<String, String> formats = new LinkedHashMap<>();
		formats.put("md", "markdown");
		formats.put("markdown", "markdown");
		formats.put("html", "html");
		formats.put("htm", "html");
		formats.put("txt", "text");
		return formats;
	}

	/** One page's text in one language; language is null for the file meant for every language. */
	public record Content(CustomPage page, String language, String format, String text) {
		public Map<String, Object> toJson() {
			Map<String, Object> body = new LinkedHashMap<>(page.toJson());
			body.put("language", language);
			body.put("format", format);
			body.put("content", text);
			return body;
		}
	}

	/** The folder holding all pages' folders. */
	public static Path root() {
		return StatsWebuiPlus.platform().configDir().resolve(StatsWebuiPlus.MOD_ID).resolve(FOLDER);
	}

	/** The configured pages that have a text, for the menu and the footer. */
	public static List<Map<String, Object>> payload(WebConfig config) {
		return payload(config, root());
	}

	static List<Map<String, Object>> payload(WebConfig config, Path root) {
		List<Map<String, Object>> shown = new ArrayList<>();
		for (CustomPage page : config.pages()) {
			if (page.enabled() && !files(root, page.id()).isEmpty()) {
				shown.add(page.toJson());
			}
		}
		return shown;
	}

	/**
	 * The page's text for a visitor, or null if there is no such page or no readable text.
	 *
	 * @param languages the visitor's languages in order, comma-separated ("it,de,en"): the web UI sends the same order
	 *                  it picks its own language from (switcher, browser languages, defaultLanguage, English)
	 */
	public static Content find(WebConfig config, String id, String languages) {
		return find(config, root(), id, languages);
	}

	/**
	 * In the first of the visitor's languages there is a file for, else the server's default language, else English,
	 * else the file for every language ({@code page.md}), else any other file (in alphabetical order).
	 */
	static Content find(WebConfig config, Path root, String id, String languages) {
		CustomPage page = config.pages().stream()
			.filter(entry -> entry.id().equals(id) && entry.enabled())
			.findFirst()
			.orElse(null);
		if (page == null) {
			return null;
		}
		List<Path> files = files(root, id);
		List<String> wanted = new ArrayList<>();
		if (languages != null) {
			for (String language : languages.split(",")) {
				String code = language.trim().toLowerCase(Locale.ROOT);
				// Language codes only: also a language the UI doesn't offer, if the server owner wrote the page in it.
				if (LANGUAGE_CODE.matcher(code).matches() && !wanted.contains(code)) {
					wanted.add(code);
				}
			}
		}
		wanted.add(config.defaultLanguage());
		wanted.add("en");
		wanted.add(NEUTRAL);
		for (String name : wanted) {
			for (Path file : files) {
				if (baseName(file).equals(name)) {
					Content content = read(page, file);
					if (content != null) {
						return content;
					}
				}
			}
		}
		for (Path file : files) {
			Content content = read(page, file);
			if (content != null) {
				return content;
			}
		}
		return null;
	}

	/** The page's text files, sorted by name and then by the order of {@link #FORMATS}. */
	private static List<Path> files(Path root, String id) {
		if (!CustomPage.isValidId(id)) {
			return List.of();
		}
		Path base = root.toAbsolutePath().normalize();
		Path folder = base.resolve(id).normalize();
		if (!folder.startsWith(base) || !Files.isDirectory(folder)) {
			return List.of();
		}
		List<String> extensions = List.copyOf(FORMATS.keySet());
		try (Stream<Path> entries = Files.list(folder)) {
			List<Path> files = entries
				.filter(file -> format(file) != null && Files.isRegularFile(file))
				.sorted((a, b) -> {
					int byName = baseName(a).compareTo(baseName(b));
					return byName != 0 ? byName : extensions.indexOf(extension(a)) - extensions.indexOf(extension(b));
				})
				.toList();
			warnAboutDuplicates(files);
			return files;
		} catch (IOException e) {
			StatsWebuiPlus.LOGGER.warn("Could not list the page folder {}: {}", folder, e.toString());
			return List.of();
		}
	}

	// Pairs already reported, so the status poll of every open tab doesn't repeat the warning.
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	/** de.md next to de.html: only the first is used (in the order of FORMATS), which is easy to miss. */
	private static void warnAboutDuplicates(List<Path> files) {
		for (int i = 1; i < files.size(); i++) {
			Path used = files.get(i - 1);
			Path ignored = files.get(i);
			if (baseName(used).equals(baseName(ignored)) && WARNED.add(ignored.toString())) {
				// The used one is the first of its name, which may be further back than i - 1.
				Path first = files.stream().filter(file -> baseName(file).equals(baseName(ignored))).findFirst().orElse(used);
				StatsWebuiPlus.LOGGER.warn("Ignoring the page {}: {} is used for that language", ignored, first.getFileName());
			}
		}
	}

	private static Content read(CustomPage page, Path file) {
		try {
			if (Files.size(file) > MAX_BYTES) {
				StatsWebuiPlus.LOGGER.warn("Leaving out {}: pages can be at most {} KiB", file, MAX_BYTES / 1024);
				return null;
			}
			String text = Files.readString(file, StandardCharsets.UTF_8);
			if (text.startsWith("﻿")) {
				text = text.substring(1);
			}
			String name = baseName(file);
			return new Content(page, name.equals(NEUTRAL) ? null : name, format(file), text);
		} catch (IOException e) {
			// Also text that isn't UTF-8 (MalformedInputException).
			StatsWebuiPlus.LOGGER.warn("Could not read the page {} (it must be UTF-8 text): {}", file, e.toString());
			return null;
		}
	}

	/** File name without the extension, lowercase: "de" for De.md. */
	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return (dot < 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
	}

	private static String extension(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String format(Path file) {
		return FORMATS.get(extension(file));
	}
}
