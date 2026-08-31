package com.cascade.api.webhook;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether a webhook target may be dialled.
 *
 * <p>Two independent checks: the host must be on the configured allowlist, and
 * it must not resolve into a private, loopback, link-local or metadata range.
 * The allowlist alone is not enough — an allowlisted name can still point at
 * 169.254.169.254.
 */
public final class SsrfGuard {

    private static final Logger LOG = LoggerFactory.getLogger(SsrfGuard.class);

    private final List<String> allowlist;
    private final boolean resolveDns;

    public SsrfGuard(List<String> allowlist) {
        this(allowlist, true);
    }

    public SsrfGuard(List<String> allowlist, boolean resolveDns) {
        this.allowlist = allowlist.stream()
                .map(SsrfGuard::normalizeHost)
                .toList();
        this.resolveDns = resolveDns;
    }

    /** Punycode-normalizes so allowlist comparison matches what we will dial. */
    static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        try {
            return IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return host.trim().toLowerCase(Locale.ROOT);
        }
    }

    static boolean isPrivateAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isCloudMetadata(address);
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        // 100.64.0.0/10
        return bytes.length == 4
                && (bytes[0] & 0xFF) == 100
                && (bytes[1] & 0xC0) == 0x40;
    }

    private static boolean isCloudMetadata(InetAddress address) {
        byte[] bytes = address.getAddress();
        // 169.254.169.254 is covered by link-local, but name it explicitly too.
        return bytes.length == 4
                && (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254
                && (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254;
    }

    public boolean isDeliverable(String target) {
        URI uri;
        try {
            uri = URI.create(target);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return false;
        }

        String host = normalizeHost(uri.getHost());
        if (host.isEmpty()) {
            return false;
        }

        boolean allowed = allowlist.stream()
                .anyMatch(entry -> host.equals(entry) || host.endsWith("." + entry));
        if (!allowed) {
            return false;
        }

        if (!resolveDns) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateAddress(address)) {
                    LOG.warn("refusing webhook to {}: resolves to {}", host, address.getHostAddress());
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            LOG.warn("refusing webhook to {}: {}", host, e.getMessage());
            return false;
        }
    }
}
