package com.omvrti.calendar_service.oauth.controller;

import com.omvrti.calendar_service.common.dto.OAuthTokenDto;
import com.omvrti.calendar_service.common.enums.ProviderType;
import com.omvrti.calendar_service.calendar.service.AccountManagementService;
import com.omvrti.calendar_service.oauth.factory.OAuthProviderFactory;
import com.omvrti.calendar_service.oauth.provider.IOAuthProvider;
import com.omvrti.calendar_service.oauth.service.TokenRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
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
    
    /**
     * Get OAuth authorization URL
     * Query params: provider (GOOGLE, OUTLOOK, etc.), redirectUri (optional)
     */
    @GetMapping("/authorize")
    public ResponseEntity<?> getAuthorizationUrl(
            @RequestParam String provider,
            @RequestParam(required = false) String redirectUri) {
        
        log.info("Requesting authorization URL for provider: {}", provider);
        
        try {
            ProviderType providerType = ProviderType.parse(provider);
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            
            String state = UUID.randomUUID().toString();
            String[] scopes = getDefaultScopes(providerType);
            String authUrl = oauthProvider.getAuthorizationUrl(state, redirectUri, scopes);
            
            Map<String, String> response = new HashMap<>();
            response.put("authorizationUrl", authUrl);
            response.put("state", state);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider: {}", provider);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error generating authorization URL", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate authorization URL: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Exchange authorization code for access token
     * Request body: { "code": "auth_code", "provider": "GOOGLE|OUTLOOK", "redirectUri": "..." }
     */
    @PostMapping("/token/exchange")
    public ResponseEntity<?> exchangeCodeForToken(
            @RequestBody Map<String, String> request) {
        
        String code = request.get("code");
        String provider = request.get("provider");
        String redirectUri = request.get("redirectUri");
        String userEmail = request.get("userEmail");
        
        log.info("Exchanging code for token - provider: {}, userEmail: {}", provider, userEmail);
        
        try {
            ProviderType providerType = ProviderType.parse(provider);
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);
            
            OAuthTokenDto tokenDto = oauthProvider.exchangeCodeForToken(code, redirectUri);
            
            // Get user email if not provided
            if (userEmail == null || userEmail.isEmpty()) {
                userEmail = oauthProvider.getUserEmail(tokenDto);
            }
            
            // Ensure we have a user record and store tokens on connected_accounts
            var user = accountManagementService.getOrCreateUser(userEmail, null, null);
            tokenRefreshService.saveOAuthTokens(user, providerType, tokenDto, userEmail);
            
            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("userEmail", userEmail);
            response.put("accessToken", tokenDto.getAccessToken());
            response.put("message", "Token exchanged and saved successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider: {}", provider);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error exchanging code for token", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to exchange code: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * OAuth callback endpoints for browser-based flows.
     * These endpoints exchange the code and persist tokens, then return a small HTML response.
     */
    @GetMapping("/callback/{provider}")
    public ResponseEntity<String> oauthCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        ProviderType providerType;

        try {
            providerType = ProviderType.parse(provider);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid provider");
        }

        if (error != null && !error.isBlank()) {
            return ResponseEntity.badRequest().body("<html><body><h3>OAuth failed</h3><p>" + escape(error) + "</p></body></html>");
        }

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("<html><body><h3>OAuth failed</h3><p>Missing code</p></body></html>");
        }

        try {
            IOAuthProvider oauthProvider = providerFactory.getProvider(providerType);

            String redirectUri = "https://kebab-recast-shrill.ngrok-free.dev/api/oauth/callback/" + provider.toLowerCase();

            OAuthTokenDto tokenDto = oauthProvider.exchangeCodeForToken(code, redirectUri);

            String userEmail = oauthProvider.getUserEmail(tokenDto);

            var user = accountManagementService.getOrCreateUser(userEmail, null, null);

            tokenRefreshService.saveOAuthTokens(user, providerType, tokenDto, userEmail);

            return ResponseEntity.ok("""
            <html>
              <body>
                <script>
                  window.location.href = "myapp://oauth-success?provider=%s&email=%s";
                </script>
                <h3>Connected successfully. You can close this window.</h3>
              </body>
            </html>
            """.formatted(providerType, userEmail));

        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return ResponseEntity.internalServerError()
                    .body("<html><body><h3>OAuth failed</h3><p>" + escape(e.getMessage()) + "</p></body></html>");
        }
    }
    
    /**
     * Refresh access token
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestParam String provider) {
        
        log.info("Refreshing token for provider: {}, userEmail: {}", provider, userEmail);
        
        try {
            ProviderType providerType = ProviderType.parse(provider);
            // Forces refresh if expired/near-expiry, otherwise returns current token
            String accessToken = tokenRefreshService.getValidAccessToken(userEmail, providerType);
            
            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("accessToken", accessToken);
            response.put("message", "Token refreshed successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider: {}", provider);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to refresh token: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * Revoke token and disconnect provider
     */
    @PostMapping("/revoke")
    public ResponseEntity<?> revokeToken(
            @RequestHeader("X-USER-EMAIL") String userEmail,
            @RequestParam String provider) {
        
        log.info("Revoking token for provider: {}, userEmail: {}", provider, userEmail);
        
        try {
            ProviderType providerType = ProviderType.parse(provider);
            accountManagementService.disconnectProvider(userEmail, providerType);
            
            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("message", "Token revoked and provider disconnected successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider: {}", provider);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid provider: " + provider);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error revoking token", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to revoke token: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    private String[] getDefaultScopes(ProviderType provider) {
        switch (provider) {
            case GOOGLE:
                return new String[]{
                        "https://www.googleapis.com/auth/calendar",
                        "https://www.googleapis.com/auth/userinfo.email",
                        "https://www.googleapis.com/auth/userinfo.profile"
                };
            case OUTLOOK:
                return new String[]{"Calendars.ReadWrite", "offline_access"};
            default:
                return new String[]{};
        }
    }

    private static String escape(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
