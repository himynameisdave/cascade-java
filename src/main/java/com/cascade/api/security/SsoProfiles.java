package com.cascade.api.security;

import com.cascade.core.model.Role;
import java.util.Locale;
import java.util.Optional;
import org.apache.xml.security.Init;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;
import org.pac4j.core.profile.CommonProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps an external identity onto a Cascade account.
 *
 * <p>Two federation paths are supported: OIDC, where the provider hands back a
 * Keycloak-shaped access token, and SAML, where a signed assertion arrives
 * instead. Both are normalized to a pac4j {@link CommonProfile} before the rest
 * of the application sees them, so only this class knows which was used.
 */
public final class SsoProfiles {

    private static final Logger LOG = LoggerFactory.getLogger(SsoProfiles.class);

    static {
        // Santuario must be initialised once before any signature is verified.
        Init.init();
    }

    private SsoProfiles() {
    }

    /** Reads the provider's access token into a normalized profile. */
    public static Optional<CommonProfile> fromAccessToken(String tokenJson) {
        try {
            AccessToken token = JsonSerialization.readValue(tokenJson, AccessToken.class);
            if (token.getEmail() == null) {
                LOG.warn("rejecting SSO token with no email claim");
                return Optional.empty();
            }

            CommonProfile profile = new CommonProfile();
            profile.setId(token.getSubject());
            profile.addAttribute("email", token.getEmail());
            profile.addAttribute("name", token.getName() == null
                    ? token.getPreferredUsername() : token.getName());
            if (token.getRealmAccess() != null) {
                profile.addAttribute("roles", token.getRealmAccess().getRoles());
            }
            return Optional.of(profile);
        } catch (Exception e) {
            LOG.warn("could not read the SSO access token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Maps provider roles onto Cascade roles. Anything unrecognised becomes a
     * viewer: an unknown external role must never grant more than read access.
     */
    public static Role toCascadeRole(Iterable<String> providerRoles) {
        Role best = Role.VIEWER;
        if (providerRoles == null) {
            return best;
        }
        for (String role : providerRoles) {
            String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT);
            if (normalized.contains("admin") && Role.ADMIN.rank() > best.rank()) {
                best = Role.ADMIN;
            } else if ((normalized.contains("member") || normalized.contains("developer"))
                    && Role.MEMBER.rank() > best.rank()) {
                best = Role.MEMBER;
            }
        }
        return best;
    }
}
