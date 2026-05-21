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
            @RequestHeader(value = "X-USER-EMAIL", required = false) String userEmail) {
        log.info("Requesting authorization URL for provider: {}", provider);
        try {
            ProviderType providerType = ProviderType.parse(provider);
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            String csrf = UUID.randomUUID().toString();
            String state = (userEmail != null && !userEmail.isBlank())
                    ? csrf + ":" + Base64.getEncoder().encodeToString(userEmail.trim().getBytes(StandardCharsets.UTF_8))
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
        String userEmail = request.get("userEmail");

        log.info("Exchanging code for token - provider: {}, userEmail: {}", provider, userEmail);
        try {
            ProviderType providerType = ProviderType.parse(provider);
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            OAuthTokenDto tokenDto = oauthProvider.exchangeCodeForToken(code, redirectUri);
            if (userEmail == null || userEmail.isBlank()) {
                userEmail = oauthProvider.getUserEmail(tokenDto);
            }
            persistOAuthTokens(userEmail, providerType, tokenDto);

            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("userEmail", userEmail);
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

            String internalEmail = decodeEmailFromState(state);
            String userEmail = (internalEmail != null) ? internalEmail : oauthProvider.getUserEmail(tokenDto);
            log.info("OAuth callback: resolved userEmail={} (from {})", userEmail,
                    internalEmail != null ? "state" : "provider");

            try {
                persistOAuthTokens(userEmail, providerType, tokenDto);
            } catch (EntityNotFoundException e) {
                log.warn("OAuth callback: no CUSTOMER_USER row for email '{}'. " +
                         "Call /api/oauth/authorize with X-USER-EMAIL set to the registered email.", userEmail);
                return ResponseEntity.badRequest().body(htmlError("User not found",
                        "No account found for <b>" + escape(userEmail) + "</b>. " +
                        "Retry the OAuth flow with the <code>X-USER-EMAIL</code> header set to your registered email address."));
            }

            log.info("OAuth callback success for {} - {}", userEmail, providerType);
            return ResponseEntity.ok("""
                <html>
                  <body>
                    <script>window.location.href = "myapp://oauth-success?provider=%s&email=%s";</script>
                    <h3>Connected successfully. You can close this window.</h3>
                  </body>
                </html>
                """.formatted(providerType, userEmail));
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

    private void persistOAuthTokens(String userEmail, ProviderType providerType, OAuthTokenDto tokenDto) {
        CustomerUserEntity customerUser =
                accountManagementService.getCustomerUser(userEmail, null, null);
        customerUserSyncService.saveTokens(customerUser, providerType, userEmail, null, tokenDto);
        log.info("OAuth tokens persisted for {} - {}", userEmail, providerType);
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

    private static String decodeEmailFromState(String state) {
        if (state == null || !state.contains(":")) return null;
        try {
            String encoded = state.split(":", 2)[1];
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
