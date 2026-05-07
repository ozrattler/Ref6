#!/usr/bin/env bash
set -euo pipefail

PB_URL="http://192.168.1.106:8090"

if ! command -v jq &>/dev/null; then
    echo "Error: jq is required. Install with: sudo apt install jq"
    exit 1
fi

read -rp  "PocketBase admin email: " EMAIL
read -rsp "PocketBase admin password: " PASSWORD
echo

# Authenticate — try v0.23+ superusers endpoint, fall back to older admins endpoint
echo "→ Authenticating..."
AUTH_BODY=$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" '{"identity":$e,"password":$p}')

authenticate() {
    curl -s -X POST "$1" -H "Content-Type: application/json" -d "$AUTH_BODY"
}

TOKEN=""
for endpoint in "/api/superusers/auth-with-password" "/api/admins/auth-with-password"; do
    response=$(authenticate "$PB_URL$endpoint")
    TOKEN=$(echo "$response" | jq -r '.token // empty')
    [[ -n "$TOKEN" ]] && break
done

if [[ -z "$TOKEN" ]]; then
    echo "Error: Authentication failed — check email/password."
    exit 1
fi
echo "  OK"

AUTH_HEADER="Authorization: Bearer $TOKEN"

# Helper: POST a collection, print its ID, exit on error
create_collection() {
    local label=$1
    local body=$2
    echo "→ Creating '$label' collection..."
    local response http_code
    response=$(curl -s -o /tmp/pb_response.json -w "%{http_code}" \
        -X POST "$PB_URL/api/collections" \
        -H "$AUTH_HEADER" \
        -H "Content-Type: application/json" \
        -d "$body")
    if [[ "$response" != 2* ]]; then
        echo "  Error (HTTP $response): $(cat /tmp/pb_response.json | jq -r '.message // .')"
        exit 1
    fi
    jq -r '.id' /tmp/pb_response.json
}

# ── matches ───────────────────────────────────────────────────────────────────
MATCHES_ID=$(create_collection "matches" '{
  "name": "matches",
  "type": "base",
  "schema": [
    {"name": "date",        "type": "date",   "required": true,  "options": {}},
    {"name": "competition", "type": "text",   "required": false, "options": {}},
    {"name": "home_team",   "type": "text",   "required": true,  "options": {}},
    {"name": "away_team",   "type": "text",   "required": true,  "options": {}},
    {"name": "final_score", "type": "text",   "required": false, "options": {}},
    {"name": "age_group",   "type": "text",   "required": false, "options": {}},
    {"name": "half_length", "type": "number", "required": false, "options": {}},
    {"name": "status",      "type": "text",   "required": false, "options": {}}
  ]
}')
echo "  Created (id: $MATCHES_ID)"

# ── incidents ─────────────────────────────────────────────────────────────────
INCIDENTS_BODY=$(jq -n --arg cid "$MATCHES_ID" '{
  "name": "incidents",
  "type": "base",
  "schema": [
    {"name": "match_id",            "type": "relation", "required": true,  "options": {"collectionId": $cid, "cascadeDelete": true, "maxSelect": 1, "displayFields": []}},
    {"name": "minute",              "type": "number",   "required": false, "options": {}},
    {"name": "type",                "type": "text",     "required": false, "options": {}},
    {"name": "team",                "type": "text",     "required": false, "options": {}},
    {"name": "player_number",       "type": "text",     "required": false, "options": {}},
    {"name": "player_name",         "type": "text",     "required": false, "options": {}},
    {"name": "offence_description", "type": "text",     "required": false, "options": {}}
  ]
}')

INCIDENTS_ID=$(create_collection "incidents" "$INCIDENTS_BODY")
echo "  Created (id: $INCIDENTS_ID)"

echo ""
echo "All done. Collections are live at $PB_URL/_/#/collections"
