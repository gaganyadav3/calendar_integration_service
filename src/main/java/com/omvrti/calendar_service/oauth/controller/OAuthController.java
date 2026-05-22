package com.omvrti.calendar_service.oauth.controller;

import com.omvrti.calendar_service.calendar.service.AccountManagementService;
import com.omvrti.calendar_service.calendar.service.CustomerUserSyncService;
import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.oauth.factory.OAuthProviderFactory;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import com.omvrti.calendar_service.persistence.entity.CustomerUserEntity;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final OAuthProviderFactory providerFactory;
    private final TokenRefreshService tokenRefreshService;
    private final AccountManagementService accountManagementService;
    private final CustomerUserSyncService customerUserSyncService;

    // ── Authorization URL ─────────────────────────────────────────────────────

    @GetMapping("/authorize")
    public ResponseEntity<?> getAuthorizationUrl(
            @RequestParam String provider,
            @RequestParam(required = false) String redirectUri,
            @RequestHeader(value = "X-USER-EMAIL", required = false) String internalEmail) {
        log.info("OAuth authorize: internalEmail={}", internalEmail);
        if (internalEmail == null || internalEmail.isBlank()) {
            log.warn("OAuth authorize: X-USER-EMAIL header is missing — callback cannot resolve CUSTOMER_USER by internal email; " +
                     "add X-USER-EMAIL header to avoid EntityNotFoundException on callback");
        }
        try {
            ProviderType providerType = ProviderType.parse(provider);
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            String csrf = UUID.randomUUID().toString();
            // URL-safe Base64 (no padding): avoids '+' → space corruption in OAuth redirect query params
            String state = (internalEmail != null && !internalEmail.isBlank())
                    ? csrf + ":" + Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(internalEmail.trim().getBytes(StandardCharsets.UTF_8))
                    : csrf;
            String authUrl = oauthProvider.getAuthorizationUrl(state, redirectUri, getDefaultScopes(providerType));
            Map<String, String> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("state", state);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid provider: " + provider);
        } catch (Exception e) {
            log.error("Error generating authorization URL", e);
            return internalError("Failed to generate authorization URL: " + e.getMessage());
        }
    }

    // ── Token exchange (API) ──────────────────────────────────────────────────

    @PostMapping("/token/exchange")
    public ResponseEntity<?> exchangeCodeForToken(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        String provider = request.get("provider");
        String redirectUri = request.get("redirectUri");
        String internalEmail = request.get("userEmail");

        log.info("Exchanging code for token - provider: {}, internalEmail: {}", provider, internalEmail);
        try {
            ProviderType providerType = ProviderType.parse(provider);
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            OAuthTokenDto tokenDto = oauthProvider.exchangeCodeForToken(code, redirectUri);
            String providerEmail = oauthProvider.getUserEmail(tokenDto);
            String lookupEmail = (internalEmail != null && !internalEmail.isBlank()) ? internalEmail : providerEmail;
            persistOAuthTokens(lookupEmail, providerEmail, providerType, tokenDto);

            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("userEmail", lookupEmail);
            response.put("accessToken", tokenDto.getAccessToken());
            response.put("message", "Token exchanged and saved successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid provider: " + provider);
        } catch (Exception e) {
            log.error("Error exchanging code for token", e);
            return internalError("Failed to exchange code: " + e.getMessage());
        }
    }

    // ── Browser callback ──────────────────────────────────────────────────────

    @GetMapping("/callback/{provider}")
    public ResponseEntity<String> oauthCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        ProviderType providerType;
        try { providerType = ProviderType.parse(provider); }
        catch (Exception e) { return ResponseEntity.badRequest().body("Invalid provider"); }

        if (error != null && !error.isBlank()) {
            log.warn("OAuth error for {}: {}", provider, error);
            return ResponseEntity.badRequest().body(htmlError("OAuth denied", escape(error)));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(htmlError("OAuth failed", "Missing authorization code"));
        }

        try {
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            String redirectUri = "https://kebab-recast-shrill.ngrok-free.dev/api/oauth/callback/"
                    + provider.toLowerCase();
            OAuthTokenDto tokenDto = oauthProvider.exchangeCodeForToken(code, redirectUri);

            // Provider email — used only for syncEmail / display, NOT for CUSTOMER_USER lookup
            String providerEmail = oauthProvider.getUserEmail(tokenDto);

            // Internal email from state — always use this for CUSTOMER_USER lookup
            String internalEmail = decodeEmailFromState(state);
            if (internalEmail != null) {
                log.info("OAuth callback: decoded internalEmail={}", internalEmail);
            } else {
                log.warn("OAuth callback: missing internal email in state — " +
                         "call /api/oauth/authorize with X-USER-EMAIL header to fix this");
            }
            String lookupEmail = (internalEmail != null) ? internalEmail : providerEmail;
            log.info("OAuth callback: resolved lookupEmail={} (from {}), providerEmail={}",
                    lookupEmail, internalEmail != null ? "state" : "provider", providerEmail);

            try {
                persistOAuthTokens(lookupEmail, providerEmail, providerType, tokenDto);
            } catch (EntityNotFoundException e) {
                log.warn("OAuth callback: no CUSTOMER_USER row for email '{}'. " +
                         "Ensure X-USER-EMAIL is the exact email stored in CUSTOMER_USER table.", lookupEmail);
                return ResponseEntity.badRequest().body(htmlError("User not found",
                        "No account found for <b>" + escape(lookupEmail) + "</b>. " +
                        "Retry the OAuth flow with the <code>X-USER-EMAIL</code> header set to your registered email address."));
            }

            log.info("OAuth callback success for {} - {}", lookupEmail, providerType);
            return ResponseEntity.ok("""
                <html>
                  <body>
                    <script>window.location.href = "myapp://oauth-success?provider=%s&email=%s";</script>
                    <h3>Connected successfully. You can close this window.</h3>
                  </body>
                </html>
                """.formatted(providerType, lookupEmail));
        } catch (Exception e) {
            log.error("OAuth callback failed for provider {}", provider, e);
            return ResponseEntity.internalServerError()
                    .body(htmlError("OAuth failed", escape(e.getMessage())));
        }
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    @PostMapping("/token/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestParam String provider) {
        log.info("Refreshing token for {} - {}", userEmail, provider);
        try {
            ProviderType providerType = ProviderType.parse(provider);
            String accessToken = customerUserSyncService.getSyncByEmail(userEmail, providerType)
                    .map(sync -> tokenRefreshService.getValidAccessToken(sync, providerType))
                    .orElseThrow(() -> new IllegalArgumentException("Provider not connected: " + provider));

            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("accessToken", accessToken);
            response.put("message", "Token refreshed successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid provider or not connected: " + provider);
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return internalError("Failed to refresh token: " + e.getMessage());
        }
    }

    // ── Revoke / disconnect ───────────────────────────────────────────────────

    @PostMapping("/revoke")
    public ResponseEntity<?> revokeToken(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestParam String provider) {
        log.info("Revoking token for {} - {}", userEmail, provider);
        try {
            ProviderType providerType = ProviderType.parse(provider);
            accountManagementService.disconnectProvider(userEmail, providerType);
            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("message", "Token revoked and provider disconnected");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid provider: " + provider);
        } catch (Exception e) {
            log.error("Error revoking token", e);
            return internalError("Failed to revoke token: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * @param internalEmail email from CUSTOMER_USER table — used for DB lookup only
     * @param syncEmail     email from the OAuth provider account — stored as sync reference
     */
    private void persistOAuthTokens(String internalEmail, String syncEmail,
                                    ProviderType providerType, OAuthTokenDto tokenDto) {
        CustomerUserEntity customerUser =
                accountManagementService.getCustomerUser(internalEmail, null, null);
        customerUserSyncService.saveTokens(customerUser, providerType, syncEmail, null, tokenDto);
        log.info("OAuth tokens persisted for internalEmail={} syncEmail={} provider={}",
                internalEmail, syncEmail, providerType);
    }

    private String[] getDefaultScopes(ProviderType provider) {
        return switch (provider) {
            case GOOGLE -> new String[]{
                    "https://www.googleapis.com/auth/calendar",
                    "https://www.googleapis.com/auth/userinfo.email",
                    "https://www.googleapis.com/auth/userinfo.profile"
            };
            case OUTLOOK -> new String[]{"Calendars.ReadWrite", "offline_access"};
            default -> new String[]{};
        };
    }

    private static ResponseEntity<Map<String, String>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private static ResponseEntity<Map<String, String>> internalError(String msg) {
        return ResponseEntity.internalServerError().body(Map.of("error", msg));
    }

    private static String htmlError(String title, String detail) {
        return "<html><body><h3>" + title + "</h3><p>" + detail + "</p></body></html>";
    }

    /**
     * Decodes the internal email encoded into the OAuth state parameter.
     * State format: {@code <csrfToken>:<urlSafeBase64Email>}
     * Returns null if state is missing, has no email segment, or Base64 is malformed.
     */
    private static String decodeEmailFromState(String state) {
        if (state == null || !state.contains(":")) return null;
        try {
            String encoded = state.split(":", 2)[1];
            if (encoded.isBlank()) return null;
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            if (decoded.isBlank()) return null;
            return decoded;
        } catch (Exception e) {
            log.warn("OAuth state decode failed — malformed Base64 in state: {}", e.getMessage());
            return null;
        }
    }

    private static String escape(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
