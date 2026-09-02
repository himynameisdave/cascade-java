package com.cascade.core.attachment;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts searchable text from uploaded attachments, and bundles a project's
 * attachments into a single archive for export.
 */
public final class AttachmentText {

    private static final Logger LOG = LoggerFactory.getLogger(AttachmentText.class);

    /** Cap on extracted characters, so a huge document cannot exhaust heap. */
    private static final int MAX_CHARS = 200_000;

    private AttachmentText() {
    }

    /**
     * Returns the plain text of an attachment, or an empty string when the type
     * has no extractable text. Extraction failures are never fatal: the
     * attachment is still stored, just not indexed.
     */
    public static String extract(byte[] content, String filename) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            new AutoDetectParser().parse(in, handler, metadata, new ParseContext());
            return handler.toString().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            LOG.warn("could not extract text from {}: {}", filename, e.getMessage());
            return "";
        }
    }

    /** Detected content type, used to validate uploads against the allowlist. */
    public static String detectType(byte[] content, String filename) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, filename);
            return new org.apache.tika.Tika().detect(in, metadata);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    /** Bundles files into one archive for a project export. */
    public static Path bundle(Path target, List<Path> files) throws IOException {
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionLevel(CompressionLevel.NORMAL);

        try (ZipFile archive = new ZipFile(target.toFile())) {
            List<File> entries = new ArrayList<>();
            for (Path file : files) {
                if (file.toFile().isFile()) {
                    entries.add(file.toFile());
                }
            }
            if (!entries.isEmpty()) {
                archive.addFiles(entries, parameters);
            }
        }
        return target;
    }
}
