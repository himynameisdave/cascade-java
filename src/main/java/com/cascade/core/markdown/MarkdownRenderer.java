package com.cascade.core.markdown;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Renders issue and comment markdown to HTML.
 *
 * <p>Issue bodies are attacker-controlled, so the pipeline is always
 * render &rarr; sanitize &rarr; linkify. Linkification runs last, on already
 * sanitized HTML, so it can only ever emit markup this class constructs.
 */
public final class MarkdownRenderer {

    private static final Pattern ISSUE_REF = Pattern.compile("\\b([A-Z][A-Z0-9]{1,9}-\\d+)\\b");
    private static final Pattern MENTION = Pattern.compile("(^|\\s)@([a-zA-Z0-9._-]{2,32})");

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true).build();

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes("code", "class")
            .addAttributes("a", "rel", "target")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    private MarkdownRenderer() {
    }

    public static String render(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        // escapeHtml(true) means raw HTML in the source is escaped rather than
        // passed through; Jsoup then enforces the tag/attribute allowlist.
        String unsafe = RENDERER.render(PARSER.parse(source));
        String clean = Jsoup.clean(unsafe, "", SAFELIST);
        return linkify(clean);
    }

    private static String linkify(String html) {
        String withRefs = ISSUE_REF.matcher(html)
                .replaceAll("<a href=\"/issues/$1\" class=\"issue-ref\">$1</a>");
        return MENTION.matcher(withRefs)
                .replaceAll("$1<span class=\"mention\">@$2</span>");
    }

    /** Plain-text excerpt used by search results and notification email. */
    public static String excerpt(String source, int maxChars) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(render(source)).text().replaceAll("\\s+", " ").trim();
        if (text.length() <= maxChars) {
            return text;
        }
        String cut = text.substring(0, maxChars);
        int lastSpace = cut.lastIndexOf(' ');
        return (lastSpace > 0 ? cut.substring(0, lastSpace) : cut) + "…";
    }

    public static List<String> extractMentions(String source) {
        Set<String> found = new LinkedHashSet<>();
        if (source != null) {
            Matcher matcher = MENTION.matcher(source);
            while (matcher.find()) {
                found.add(matcher.group(2).toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(found);
    }

    public static List<String> extractIssueRefs(String source) {
        Set<String> found = new LinkedHashSet<>();
        if (source != null) {
            Matcher matcher = ISSUE_REF.matcher(source);
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
        }
        return new ArrayList<>(found);
    }
}
