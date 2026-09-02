package com.cascade.core.importer;

import java.io.StringReader;
import java.util.List;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;

/**
 * Reads a Jira "XML issue export" ({@code rss > channel > item}).
 *
 * <p>The reader is configured to reject DTDs and external entities: import
 * files are uploaded by users, and an XML parser that resolves entities is an
 * XXE hole.
 */
public final class JiraXmlImporter {

    private JiraXmlImporter() {
    }

    private static SAXReader hardenedReader() throws SAXException {
        SAXReader reader = new SAXReader();
        reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setIncludeExternalDTDDeclarations(false);
        reader.setIncludeInternalDTDDeclarations(false);
        return reader;
    }

    private static String text(Element item, String name) {
        Element child = item.element(name);
        return child == null ? "" : child.getTextTrim();
    }

    public static ImportReport parse(String content) {
        ImportReport report = new ImportReport();
        Document document;
        try {
            document = hardenedReader().read(new StringReader(content));
        } catch (DocumentException | SAXException e) {
            report.skip(0, "could not parse the XML: " + e.getMessage());
            return report;
        }

        Element channel = document.getRootElement().element("channel");
        if (channel == null) {
            report.skip(0, "no <channel> element: this does not look like a Jira export");
            return report;
        }

        List<Element> items = channel.elements("item");
        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            String title = text(item, "title");
            if (title.isEmpty()) {
                report.skip(i + 1, "<item> has no <title>");
                continue;
            }

            ImportRow row = new ImportRow();
            row.setTitle(title);
            row.setDescription(text(item, "description"));
            row.setStatus(Normalizers.status(text(item, "status")));
            row.setPriority(Normalizers.priority(text(item, "priority")));
            row.setType(Normalizers.type(text(item, "type")));
            row.setAssigneeEmail(Normalizers.trimToNull(text(item, "assignee")));
            row.setStoryPoints(Normalizers.points(text(item, "storyPoints")));
            row.setDueDate(Normalizers.date(text(item, "due")));

            Element labels = item.element("labels");
            if (labels != null) {
                StringBuilder joined = new StringBuilder();
                for (Element label : labels.elements("label")) {
                    if (joined.length() > 0) {
                        joined.append(',');
                    }
                    joined.append(label.getTextTrim());
                }
                row.setLabels(Normalizers.labels(joined.toString()));
            }
            report.add(row);
        }
        return report;
    }
}
