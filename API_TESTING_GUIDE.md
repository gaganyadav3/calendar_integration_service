# API Testing Guide - Postman Collection

## Base URL
```
https://kebab-recast-shrill.ngrok-free.dev
```

---

## 1. OAUTH SETUP ENDPOINTS

### 1.1 Get Google Authorization URL
```
GET /api/oauth/authorize?provider=GOOGLE&redirectUri=http://localhost:3000/callback
```

**Postman Setup:**
- Method: GET
- URL: {{BASE_URL}}/api/oauth/authorize?provider=GOOGLE&redirectUri=http://localhost:3000/callback
- Headers: None

**Response (200):**
```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=808325637026-ed3cg3rp954cn6lnch186242noq728qf.apps.googleusercontent.com&redirect_uri=https%3A%2F%2Fkebab-recast-shrill.ngrok-free.dev%2Fapi%2Foauth%2Fcallback%2Fgoogle&response_type=code&scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar&state=user@example.com",
  "state": "user@example.com"
}
```

**Next Steps:**
1. Copy `authorizationUrl` and open in browser
2. User logs in and approves permissions
3. Browser redirects to callback with `code` parameter
4. Use code in next endpoint

---

### 1.2 Get Outlook Authorization URL
```
GET /api/oauth/authorize?provider=OUTLOOK
```

**Postman Setup:**
- Method: GET
- URL: {{BASE_URL}}/api/oauth/authorize?provider=OUTLOOK
- Headers: None

**Response (200):**
```json
{
  "authorizationUrl": "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=e10025d3-6ca2-4c1c-bd31-509933da96df&redirect_uri=https%3A%2F%2Fkebab-recast-shrill.ngrok-free.dev%2Fapi%2Foauth%2Fcallback%2Foutlook&response_type=code&scope=offline_access%20Calendars.ReadWrite&state=user@example.com",
  "state": "user@example.com"
}
```

---

### 1.3 Exchange Authorization Code for Token
```
POST /api/oauth/token/exchange
```

**Postman Setup:**
- Method: POST
- URL: {{BASE_URL}}/api/oauth/token/exchange
- Headers: 
  - Content-Type: application/json
- Body (raw JSON):
```json
{
  "code": "4/0AY0e-g7...",
  "provider": "GOOGLE",
  "redirectUri": "https://kebab-recast-shrill.ngrok-free.dev/api/oauth/callback/google",
  "userEmail": "user@example.com"
}
```

**Response (200):**
```json
{
  "success": true,
  "userEmail": "user@example.com",
  "provider": "GOOGLE",
  "accessToken": "ya29.a0AfH6SMBx...",
  "refreshToken": "1//0gXxxxx...",
  "expiresIn": 3599,
  "message": "Token exchanged and saved successfully"
}
```

**HTTP Status Codes:**
- `200` - Token successfully exchanged and stored
- `400` - Invalid code or provider
- `500` - Token exchange failed

---

### 1.4 Refresh Token
```
POST /api/oauth/token/refresh?provider=GOOGLE
```

**Postman Setup:**
- Method: POST
- URL: {{BASE_URL}}/api/oauth/token/refresh?provider=GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200):**
```json
{
  "success": true,
  "userEmail": "user@example.com",
  "accessToken": "ya29.a0AfH6SMBz...",
  "expiresIn": 3599,
  "message": "Token refreshed successfully"
}
```

---

### 1.5 Revoke/Disconnect Account
```
POST /api/oauth/revoke?provider=GOOGLE
```

**Postman Setup:**
- Method: POST
- URL: {{BASE_URL}}/api/oauth/revoke?provider=GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200):**
```json
{
  "success": true,
  "message": "Token revoked and provider disconnected successfully"
}
```

---

## 2. CALENDAR SYNC ENDPOINTS

### 2.1 Trigger Full Sync
```
POST /api/sync/{provider}
```

**Postman Setup:**
- Method: POST
- URL: {{BASE_URL}}/api/sync/GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200/202):**
```json
{
  "success": true,
  "provider": "GOOGLE",
  "message": "Sync initiated for user@example.com",
  "syncId": "sync-uuid-1234..."
}
```

**Status Transitions:**
- Sync initiated (PENDING)
- Fetching calendars (IN_PROGRESS)
- Processing events (IN_PROGRESS)
- Processing webhooks (IN_PROGRESS)
- Sync completed (COMPLETED)
- Sync failed (FAILED)

---

### 2.2 Get Sync Status
```
GET /api/sync/status/{syncId}
```

**Postman Setup:**
- Method: GET
- URL: {{BASE_URL}}/api/sync/status/sync-uuid-1234
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200):**
```json
{
  "syncId": "sync-uuid-1234",
  "provider": "GOOGLE",
  "status": "COMPLETED",
  "startTime": "2026-05-22T10:00:00Z",
  "endTime": "2026-05-22T10:05:30Z",
  "calendarsProcessed": 3,
  "eventsProcessed": 47,
  "errors": [],
  "nextSyncTime": "2026-05-22T10:05:00Z"
}
```

---

### 2.3 Get All Events (Database)
```
GET /api/calendar/events
```

**Postman Setup:**
- Method: GET
- URL: {{BASE_URL}}/api/calendar/events
- Headers:
  - X-USER-EMAIL: user@example.com
- Query Params (Optional):
  - page: 0
  - size: 20

**Response (200):**
```json
{
  "success": true,
  "count": 47,
  "events": [
    {
      "id": "cu-sync-event-123",
      "externalId": "google-event-abc123",
      "provider": "GOOGLE",
      "summary": "Team Meeting",
      "description": "Weekly sync with team",
      "location": "Conference Room A",
      "startDateTime": "2026-05-22T10:00:00+00:00",
      "endDateTime": "2026-05-22T11:00:00+00:00",
      "startDate": "2026-05-22",
      "endDate": "2026-05-22",
      "status": "CONFIRMED",
      "organizer": "organizer@example.com",
      "isAllDay": false,
      "isRecurring": false,
      "recurringEventId": null,
      "guests": [
        {
          "guestName": "John Doe",
          "guestEmail": "john@example.com",
          "responseStatus": "ACCEPTED",
          "isOrganiser": false,
          "isOptional": false
        }
      ],
      "reminders": [
        {
          "type": "NOTIFICATION",
          "minutesBefore": 15,
          "reminderMethod": "EMAIL"
        }
      ]
    }
  ]
}
```

---

### 2.4 Get Events by Provider
```
GET /api/calendar/events/by-provider?provider=GOOGLE
```

**Postman Setup:**
- Method: GET
- URL: {{BASE_URL}}/api/calendar/events/by-provider?provider=GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200):**
```json
{
  "success": true,
  "provider": "GOOGLE",
  "count": 25,
  "events": [  ]
}
```

---

### 2.5 Get Specific Event Details
```
GET /api/calendar/events/{eventId}
```

**Postman Setup:**
- Method: GET
- URL: {{BASE_URL}}/api/calendar/events/cu-sync-event-123
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200):**
```json
{
  "success": true,
  "event": {
    "id": "cu-sync-event-123",
    "externalId": "google-event-abc123",
    "provider": "GOOGLE",
    "summary": "Team Meeting",
    "description": "Weekly sync with team",
    "location": "Conference Room A",
    "startDateTime": "2026-05-22T10:00:00+00:00",
    "endDateTime": "2026-05-22T11:00:00+00:00",
    "status": "CONFIRMED",
    "organizer": "organizer@example.com",
    "isAllDay": false,
    "guests": [
      {
        "id": "cu-sync-guest-456",
        "guestName": "John Doe",
        "guestEmail": "john@example.com",
        "responseStatus": "ACCEPTED",
        "isOrganiser": false,
        "isOptional": false,
        "isHuman": true
      }
    ],
    "reminders": [
      {
        "id": "event-reminder-789",
        "type": "NOTIFICATION",
        "minutesBefore": 15,
        "reminderMethod": "EMAIL"
      }
    ],
    "webhooks": [
      {
        "id": "webhook-001",
        "provider": "GOOGLE",
        "channelId": "channel-abc123",
        "resourceId": "resource-xyz789",
        "expiresAt": "2026-05-29T10:00:00Z"
      }
    ]
  }
}
```

---

## 3. MANUAL EVENT OPERATIONS

### 3.1 Create Event
```
POST /api/calendar/events?provider=GOOGLE
```

**Postman Setup:**
- Method: POST
- URL: {{BASE_URL}}/api/calendar/events?provider=GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com
  - Content-Type: application/json
- Body (raw JSON):
```json
{
  "summary": "New Meeting",
  "description": "Description of meeting",
  "location": "Room 101",
  "startDateTime": "2026-05-25T14:00:00Z",
  "endDateTime": "2026-05-25T15:00:00Z",
  "status": "CONFIRMED",
  "isAllDay": false,
  "guests": [
    {
      "guestEmail": "attendee@example.com",
      "guestName": "John Doe",
      "responseStatus": "NEEDS_ACTION"
    }
  ],
  "reminders": [
    {
      "minutesBefore": 15,
      "reminderMethod": "EMAIL"
    }
  ]
}
```

**Response (201):**
```json
{
  "success": true,
  "eventId": "cu-sync-event-new-001",
  "externalEventId": "google-event-def456",
  "provider": "GOOGLE",
  "message": "Event created successfully"
}
```

---

### 3.2 Update Event
```
PUT /api/calendar/events/{eventId}?provider=GOOGLE
```

**Postman Setup:**
- Method: PUT
- URL: {{BASE_URL}}/api/calendar/events/cu-sync-event-123?provider=GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com
  - Content-Type: application/json
- Body (raw JSON):
```json
{
  "summary": "Updated Meeting Title",
  "description": "Updated description",
  "location": "Room 202",
  "startDateTime": "2026-05-25T15:00:00Z",
  "endDateTime": "2026-05-25T16:00:00Z"
}
```

**Response (200):**
```json
{
  "success": true,
  "message": "Event updated successfully"
}
```

---

### 3.3 Delete Event
```
DELETE /api/calendar/events/{eventId}?provider=GOOGLE
```

**Postman Setup:**
- Method: DELETE
- URL: {{BASE_URL}}/api/calendar/events/cu-sync-event-123?provider=GOOGLE
- Headers:
  - X-USER-EMAIL: user@example.com

**Response (200):**
```json
{
  "success": true,
  "message": "Event deleted successfully"
}
```

---

### 3.4 Batch Create Events
```
POST /api/calendar/events/batch
```

**Postman Setup:**
- Method: POST
- URL: {{BASE_URL}}/api/calendar/events/batch
- Headers:
  - X-USER-EMAIL: user@example.com
  - Content-Type: application/json
- Body (raw JSON):
```json
{
  "provider": "GOOGLE",
  "events": [
    {
      "summary": "Event 1",
      "startDateTime": "2026-05-25T10:00:00Z",
      "endDateTime": "2026-05-25T11:00:00Z"
    },
    {
      "summary": "Event 2",
      "startDateTime": "2026-05-25T14:00:00Z",
      "endDateTime": "2026-05-25T15:00:00Z"
    }
  ]
}
```

**Response (201):**
```json
{
  "success": true,
  "count": 2,
  "eventIds": ["cu-sync-event-batch-1", "cu-sync-event-batch-2"],
  "message": "Events created successfully"
}
```

---

## 4. ERROR RESPONSES

### 4.1 Token Not Found
```json
{
  "error": "TokenNotFoundException",
  "errorCode": "NO_TOKEN",
  "message": "OAuth token not found for provider GOOGLE",
  "cause": "User has not connected their Google account"
}
```

---

### 4.2 Token Expired and Refresh Failed
```json
{
  "error": "TokenRefreshException",
  "errorCode": "REFRESH_FAILED",
  "message": "Failed to refresh token for provider GOOGLE",
  "cause": "Refresh token expired or revoked. User must re-authenticate."
}
```

---

### 4.3 Sync Already In Progress
```json
{
  "error": "SyncInProgressException",
  "errorCode": "SYNC_IN_PROGRESS",
  "message": "Sync already in progress for user@example.com on provider GOOGLE",
  "cause": "Wait for previous sync to complete"
}
```

---

### 4.4 Validation Errors
```json
{
  "error": "ValidationException",
  "errorCode": "VALIDATION_ERROR",
  "message": "Event summary is required",
  "cause": "summary field cannot be empty"
}
```

---

## 5. POSTMAN COLLECTION SETUP

### Import Variables
Create a Postman Collection and add these variables:

```json
{
  "BASE_URL": "https://kebab-recast-shrill.ngrok-free.dev",
  "USER_EMAIL": "user@example.com",
  "PROVIDER": "GOOGLE",
  "ACCESS_TOKEN": "ya29.a0AfH6SMBx...",
  "SYNC_ID": "sync-uuid-1234",
  "EVENT_ID": "cu-sync-event-123"
}
```

### Pre-request Script (for error handling)
```javascript
// Check if token needs refresh
pm.environment.get("LAST_TOKEN_REFRESH");
```

### Tests (for status validation)
```javascript
pm.test("Response status is 200 or 202", function() {
    pm.expect(pm.response.code).to.be.oneOf([200, 202]);
});

pm.test("Response has success field", function() {
    pm.expect(pm.response.json()).to.have.property("success");
});

pm.test("Response contains data", function() {
    var data = pm.response.json();
    pm.expect(data).to.not.be.empty;
});
```

---

## 6. TESTING SCENARIOS

### Scenario 1: Full OAuth Callback Flow
1. GET /api/oauth/authorize?provider=GOOGLE → Get authorizationUrl
2. Open URL in browser → User logs in
3. Browser redirects with `code` parameter
4. POST /api/oauth/token/exchange → Exchange code for tokens
5. Tokens stored in CUSTOMER_USER_SYNC table
6. GET /api/oauth/token/status → Verify tokens saved

### Scenario 2: Incremental Sync via Webhook
1. POST /api/webhook/register/google → Register webhook channel
2. Event changes in Google Calendar
3. Google sends webhook notification
4. POST /api/webhook/google → SyncEngine.triggerIncrementalSync()
5. GET /api/sync/status/{syncId} → Check incremental sync status
6. GET /api/calendar/events → Verify updated events

### Scenario 3: Manual Sync Trigger
1. Verify user has connected account (tokens exist)
2. POST /api/sync/GOOGLE → Trigger full sync
3. Monitor database tables (CU_SYNC_CALENDAR_EVENT grows)
4. GET /api/calendar/events → Verify all events synced
5. Check sync duration: target < 5 seconds for 50 events

---

## 7. PERFORMANCE BENCHMARKS

| Operation | Target Duration | Acceptable Range |
|-----------|-----------------|-----------------|
| Token Exchange | < 2s | 1-3s |
| Fetch 50 events | < 1s | 0.5-2s |
| Sync 3 calendars (50 events) | < 5s | 3-8s |
| Webhook notification handling | < 500ms | 200-1000ms |
| Event creation | < 1s | 0.5-2s |
| Batch create 10 events | < 3s | 2-5s |

---

## 8. CURL COMMAND EXAMPLES

### Get Authorization URL
```bash
curl -X GET "https://kebab-recast-shrill.ngrok-free.dev/api/oauth/authorize?provider=GOOGLE" \
  -H "Content-Type: application/json"
```

### Exchange Token
```bash
curl -X POST "https://kebab-recast-shrill.ngrok-free.dev/api/oauth/token/exchange" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "4/0AY0e-g7...",
    "provider": "GOOGLE",
    "redirectUri": "https://kebab-recast-shrill.ngrok-free.dev/api/oauth/callback/google",
    "userEmail": "user@example.com"
  }'
```

### Trigger Sync
```bash
curl -X POST "https://kebab-recast-shrill.ngrok-free.dev/api/sync/GOOGLE" \
  -H "X-USER-EMAIL: user@example.com"
```

### Create Event
```bash
curl -X POST "https://kebab-recast-shrill.ngrok-free.dev/api/calendar/events?provider=GOOGLE" \
  -H "Content-Type: application/json" \
  -H "X-USER-EMAIL: user@example.com" \
  -d '{
    "summary": "New Meeting",
    "startDateTime": "2026-05-25T14:00:00Z",
    "endDateTime": "2026-05-25T15:00:00Z"
  }'
```


