package com.cascade.core.importer;

import java.io.StringReader;
import java.util.List;
import org.codehaus.jackson.map.DeserializationConfig;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads exports produced by Cascade 0.4 and earlier.
 *
 * <p>Those releases wrote a different XML shape and a JSON sidecar serialized
 * by Jackson 1.x, so this path keeps the old readers rather than reshaping
 * every historical export. New exports go through {@link JiraXmlImporter} and
 * {@link CsvImporter}.
 */
public final class LegacyImporter {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyImporter.class);

    /** Jackson 1.x mapper, kept only for the historical sidecar format. */
    private static final org.codehaus.jackson.map.ObjectMapper LEGACY_JSON =
            new org.codehaus.jackson.map.ObjectMapper()
                    .configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private LegacyImporter() {
    }

    private static SAXBuilder hardenedBuilder() {
        SAXBuilder builder = new SAXBuilder();
        // Import files are user-supplied: never resolve external entities.
        builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        builder.setExpandEntities(false);
        return builder;
    }

    /** Parses the pre-0.5 {@code <cascade><issues><issue>} export shape. */
    public static ImportReport parseLegacyXml(String content) {
        ImportReport report = new ImportReport();
        try {
            Document document = hardenedBuilder().build(new StringReader(content));
            Element issues = document.getRootElement().getChild("issues");
            if (issues == null) {
                report.skip(0, "no <issues> element: this is not a Cascade 0.4 export");
                return report;
            }

            List<Element> children = issues.getChildren("issue");
            for (int i = 0; i < children.size(); i++) {
                Element element = children.get(i);
                String summary = element.getChildTextTrim("summary");
                if (summary == null || summary.isEmpty()) {
                    report.skip(i + 1, "<issue> has no <summary>");
                    continue;
                }
                ImportRow row = new ImportRow();
                row.setTitle(summary);
                row.setDescription(text(element, "body"));
                row.setStatus(Normalizers.status(text(element, "state")));
                row.setPriority(Normalizers.priority(text(element, "importance")));
                row.setType(Normalizers.type(text(element, "kind")));
                row.setLabels(Normalizers.labels(text(element, "tags")));
                row.setAssigneeEmail(Normalizers.trimToNull(text(element, "owner")));
                row.setStoryPoints(Normalizers.points(text(element, "estimate")));
                row.setDueDate(Normalizers.date(text(element, "deadline")));
                report.add(row);
            }
        } catch (JDOMException | java.io.IOException e) {
            report.skip(0, "could not parse the legacy export: " + e.getMessage());
        }
        return report;
    }

    private static String text(Element element, String name) {
        String value = element.getChildTextTrim(name);
        return value == null ? "" : value;
    }

    /** Reads the label sidecar the old exporter wrote next to the XML. */
    public static List<String> parseLegacyLabelSidecar(String json) {
        try {
            return LEGACY_JSON.readValue(json, List.class);
        } catch (Exception e) {
            LOG.warn("ignoring unreadable legacy label sidecar: {}", e.getMessage());
            return List.of();
        }
    }
}
