# Calendar Service - Multi-Provider Calendar Integration

A Spring Boot microservice for integrating multiple calendar providers (Google Calendar, Outlook, and future providers) with OAuth 2.0 authentication and centralized event management.

## Architecture Overview

```
com.omvrti.calendar_service/
├── config/                 # Spring configuration
├── common/                 # Shared utilities, exceptions, DTOs, enums
│   ├── dto/               # Data Transfer Objects
│   ├── enums/             # ProviderType, OAuthScope enums
│   ├── exception/         # Custom exceptions
│   └── util/              # Utility classes
├── calendar/              # Calendar management
│   ├── controller/        # Calendar REST APIs
│   ├── service/           # Calendar orchestration service
│   ├── factory/           # Provider factory pattern
│   └── provider/          # Calendar provider implementations
│       ├── ICalendarProvider.java  (Interface)
│       └── impl/          # Google, Outlook, Zoho, etc.
├── oauth/                 # OAuth token management
│   ├── controller/        # OAuth REST APIs
│   ├── service/           # Token service & refresh logic
│   ├── factory/           # Provider factory pattern
│   └── provider/          # OAuth provider implementations
│       ├── IOAuthProvider.java  (Interface)
│       └── impl/          # Google, Outlook, Zoho, etc.
├── persistence/           # Database layer
│   ├── entity/            # JPA entities (CalendarEventEntity, OAuthTokenEntity)
│   └── repository/        # Spring Data JPA repositories
└── Application.java       # Spring Boot entry point
```

## Design Patterns Used

### 1. Factory Pattern
- **CalendarProviderFactory**: Manages instantiation of calendar providers
- **OAuthProviderFactory**: Manages instantiation of OAuth providers
- Allows easy addition of new providers without modifying existing code

### 2. Strategy Pattern
- **ICalendarProvider Interface**: Different implementations for each calendar provider
- **IOAuthProvider Interface**: Different implementations for each OAuth provider
- Enables runtime selection of appropriate provider

### 3. Dependency Injection
- Spring Bean configuration registers all providers
- Services receive dependencies through constructor injection

### 4. Repository Pattern
- Abstracted database access through Spring Data JPA
- Clean separation between business logic and data access

## Supported Calendar Providers

### Currently Implemented
- **Google Calendar**: Full support via Google Calendar API v3
- **Outlook Calendar**: Full support via Microsoft Graph API

### Planned (Stub Classes Ready)
- Zoho Calendar
- Apple Calendar
- Calendly
- Thunderbird

## Getting Started

### Prerequisites
- Java 21+
- Spring Boot 4.1.0+
- Oracle Database (or H2 for development)
- Maven 3.8+

### Setup Instructions

#### 1. Google Calendar OAuth Setup
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project
3. Enable "Google Calendar API"
4. Create OAuth 2.0 credentials (Web Application)
5. Add redirect URI: `http://localhost:8090/api/oauth/callback/google`
6. Copy Client ID and Client Secret

#### 2. Microsoft Outlook OAuth Setup
1. Go to [Azure Portal](https://portal.azure.com/)
2. Create a new application registration
3. Add redirect URI: `http://localhost:8090/api/oauth/callback/outlook`
4. Create a client secret
5. Grant permissions: `Calendars.ReadWrite`, `offline_access`
6. Copy Client ID and Client Secret

#### 3. Configure application.properties
```properties
# Google OAuth
oauth.google.client-id=YOUR_GOOGLE_CLIENT_ID
oauth.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
oauth.google.redirect-uri=http://localhost:8090/api/oauth/callback/google

# Outlook OAuth
oauth.outlook.client-id=YOUR_OUTLOOK_CLIENT_ID
oauth.outlook.client-secret=YOUR_OUTLOOK_CLIENT_SECRET
oauth.outlook.redirect-uri=http://localhost:8090/api/oauth/callback/outlook

# Database
spring.datasource.url=jdbc:oracle:thin:@//your-host:1521/your-db
spring.datasource.username=your-username
spring.datasource.password=your-password
```

#### 4. Run the Application
```bash
mvn clean install
mvn spring-boot:run
```

The service will start on `http://localhost:8090`

## API Documentation

### OAuth Endpoints

#### 1. Get Authorization URL
```
GET /api/oauth/authorize?provider=GOOGLE&redirectUri=http://localhost:3000/callback
```

**Query Parameters:**
- `provider` (required): GOOGLE or OUTLOOK
- `redirectUri` (optional): Custom redirect URI

**Response:**
```json
{
  "authorizationUrl": "https://...",
  "state": "uuid-string"
}
```

#### 2. Exchange Code for Token
```
POST /api/oauth/token/exchange
```

**Request Body:**
```json
{
  "code": "authorization_code_from_provider",
  "provider": "GOOGLE|OUTLOOK",
  "redirectUri": "http://localhost:3000/callback",
  "userEmail": "user@example.com"
}
```

**Response:**
```json
{
  "success": "true",
  "userEmail": "user@example.com",
  "accessToken": "token_string",
  "message": "Token exchanged and saved successfully"
}
```

#### 3. Refresh Token
```
POST /api/oauth/token/refresh?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com
```

**Response:**
```json
{
  "success": "true",
  "accessToken": "new_access_token",
  "message": "Token refreshed successfully"
}
```

#### 4. Revoke Token (Disconnect Provider)
```
POST /api/oauth/revoke?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com
```

**Response:**
```json
{
  "success": "true",
  "message": "Token revoked and provider disconnected successfully"
}
```

### Calendar Endpoints

#### 1. Fetch Events from Provider
```
GET /api/calendar/events/fetch?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com
```

**Response:**
```json
{
  "success": true,
  "provider": "GOOGLE",
  "count": 15,
  "events": [
    {
      "id": "event_id",
      "summary": "Meeting Title",
      "description": "Meeting Description",
      "location": "Conference Room",
      "startDateTime": "2026-04-28T10:00:00+00:00",
      "endDateTime": "2026-04-28T11:00:00+00:00",
      "status": "CONFIRMED",
      "allDay": false,
      "organizer": "organizer@example.com"
    }
  ]
}
```

#### 2. Save Single Event
```
POST /api/calendar/events?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com

Body:
{
  "summary": "Team Meeting",
  "description": "Weekly sync",
  "location": "Room 101",
  "startDateTime": "2026-04-29T14:00:00+00:00",
  "endDateTime": "2026-04-29T15:00:00+00:00",
  "status": "CONFIRMED",
  "allDay": false
}
```

**Response:**
```json
{
  "success": true,
  "eventId": "external_event_id",
  "message": "Event saved successfully"
}
```

#### 3. Save Multiple Events
```
POST /api/calendar/events/save
Header: X-USER-EMAIL: user@example.com

Body:
{
  "provider": "GOOGLE",
  "events": [
    { "summary": "Event 1", ... },
    { "summary": "Event 2", ... }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "Events saved successfully",
  "count": 2
}
```

#### 4. Update Event
```
PUT /api/calendar/events/{eventId}?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com

Body: { updated event data }
```

#### 5. Delete Event
```
DELETE /api/calendar/events/{eventId}?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com
```

#### 6. Get All User Events (from Database)
```
GET /api/calendar/events
Header: X-USER-EMAIL: user@example.com
```

#### 7. Get User Events by Provider (from Database)
```
GET /api/calendar/events/by-provider?provider=GOOGLE
Header: X-USER-EMAIL: user@example.com
```

## Database Schema

### OAUTH_TOKENS Table
```sql
CREATE TABLE OAUTH_TOKENS (
  id                NUMBER PRIMARY KEY,
  user_email        VARCHAR2(255) NOT NULL,
  provider          VARCHAR2(50) NOT NULL,
  access_token      CLOB NOT NULL,
  refresh_token     CLOB,
  expires_in        NUMBER,
  token_type        VARCHAR2(50),
  scope             CLOB,
  created_at        TIMESTAMP,
  updated_at        TIMESTAMP,
  refreshed_at      TIMESTAMP,
  UNIQUE (user_email, provider)
);
```

### CALENDAR_EVENTS Table
```sql
CREATE TABLE CALENDAR_EVENTS (
  id                VARCHAR2(255) PRIMARY KEY,
  user_email        VARCHAR2(255) NOT NULL,
  provider          VARCHAR2(50),
  external_id       VARCHAR2(255),
  summary           VARCHAR2(500),
  description       CLOB,
  location          VARCHAR2(255),
  start_date_time   TIMESTAMP,
  end_date_time     TIMESTAMP,
  start_date        DATE,
  end_date          DATE,
  status            VARCHAR2(50),
  organizer         VARCHAR2(255),
  is_all_day        CHAR(1) DEFAULT 'N',
  created_at        TIMESTAMP,
  updated_at        TIMESTAMP
);
```

## Adding New Providers

### Step 1: Create OAuth Provider Implementation
```java
@Component
public class NewProviderOAuthProvider implements IOAuthProvider {
    // Implement all required methods
}
```

### Step 2: Create Calendar Provider Implementation
```java
@Component
public class NewProviderCalendarProvider implements ICalendarProvider {
    // Implement all required methods
}
```

### Step 3: Register in ProvidersConfiguration
```java
@Bean
public Map<ProviderType, IOAuthProvider> oauthProviders(..., NewProviderOAuthProvider provider) {
    providers.put(ProviderType.NEWPROVIDER, provider);
    return providers;
}
```

### Step 4: Add ProviderType Enum Value
```java
public enum ProviderType {
    GOOGLE,
    OUTLOOK,
    NEWPROVIDER
}
```

## Error Handling

All endpoints return standardized error responses:

```json
{
  "error": "ExceptionType",
  "errorCode": "ERROR_CODE",
  "message": "Detailed error message",
  "cause": "Root cause if available"
}
```

### Common Error Codes
- `NO_TOKEN`: OAuth token not found
- `TOKEN_EXCHANGE_FAILED`: Failed to exchange authorization code
- `REFRESH_FAILED`: Failed to refresh token
- `PROVIDER_NOT_FOUND`: Requested provider not implemented
- `FETCH_EVENTS_FAILED`: Failed to fetch events from provider
- `CREATE_EVENT_FAILED`: Failed to create event
- `UPDATE_EVENT_FAILED`: Failed to update event
- `DELETE_EVENT_FAILED`: Failed to delete event

## Security Considerations

1. **Token Encryption**: Refresh tokens are stored in the database and should be encrypted
2. **Token Expiration**: Access tokens are automatically refreshed with a 5-minute safety margin
3. **HTTPS**: Always use HTTPS in production
4. **OAuth Scopes**: Minimal required scopes are requested from providers
5. **CORS**: Configure CORS appropriately for your frontend domain

## Logging

Logging is configured at different levels:
- Root: INFO
- Application: DEBUG (includes provider operations)

Configure in `application.properties`:
```properties
logging.level.root=INFO
logging.level.com.omvrti.calendar_service=DEBUG
```

## Performance Optimization

1. **Token Caching**: Valid tokens are cached; only expired tokens are refreshed
2. **Pagination**: Event fetching supports pagination (currently uses 250 items per page)
3. **Batch Operations**: Events can be saved in batches
4. **Connection Pooling**: RestTemplate uses pooled connections

## Testing

Run unit tests:
```bash
mvn test
```

### Example Test Case
```java
@SpringBootTest
class CalendarServiceTest {
    @Autowired
    private CalendarService service;
    
    @Test
    void testSaveEvent() {
        CalendarEventDto event = CalendarEventDto.builder()
            .summary("Test Event")
            .startDateTime(OffsetDateTime.now())
            .build();
        
        String eventId = service.saveEvent("user@example.com", ProviderType.GOOGLE, event);
        assertNotNull(eventId);
    }
}
```

## Troubleshooting

### Issue: 401 Unauthorized when fetching events
**Solution**: OAuth token has expired. Call the refresh token endpoint first.

### Issue: 403 Forbidden from provider
**Solution**: Check that the OAuth scopes are correct and the app has permissions.

### Issue: Database connection failures
**Solution**: Verify database credentials in `application.properties` and ensure the database is accessible.

### Issue: Token refresh keeps failing
**Solution**: The refresh token may have been revoked. User needs to re-authenticate through OAuth flow.

## Future Enhancements

1. **Event Synchronization**: Sync events across multiple providers
2. **Conflict Resolution**: Handle event conflicts when syncing
3. **Caching Layer**: Redis caching for frequently accessed data
4. **WebSocket Support**: Real-time event updates
5. **Advanced Scheduling**: Scheduled event sync tasks
6. **Event Filtering**: Advanced filtering and search capabilities
7. **Timezone Support**: Better timezone handling across providers

## Contributing

When adding new providers:
1. Implement both `IOAuthProvider` and `ICalendarProvider` interfaces
2. Add provider-specific configuration in `application.properties`
3. Register providers in `ProvidersConfiguration`
4. Update documentation
5. Add unit tests

## License

Proprietary - OMVRTI

## Support

For issues or questions, contact the development team.

