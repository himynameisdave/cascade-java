package com.cascade.core.markdown;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rasterizes inline SVG diagrams from issue descriptions so they can be
 * embedded in PDF and email, neither of which renders SVG reliably.
 */
public final class DiagramRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(DiagramRenderer.class);

    private DiagramRenderer() {
    }

    /** Returns PNG bytes, or an empty array when the SVG cannot be rendered. */
    public static byte[] toPng(String svg, float width) {
        if (svg == null || svg.isBlank()) {
            return new byte[0];
        }
        try {
            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transcoder.transcode(
                    new TranscoderInput(new ByteArrayInputStream(
                            svg.getBytes(StandardCharsets.UTF_8))),
                    new TranscoderOutput(out));
            out.flush();
            return out.toByteArray();
        } catch (TranscoderException | IOException e) {
            LOG.warn("could not rasterize diagram: {}", e.getMessage());
            return new byte[0];
        }
    }
}
