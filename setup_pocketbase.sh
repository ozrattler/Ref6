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
TOKEN=""
for endpoint in "/api/superusers/auth-with-password" "/api/admins/auth-with-password"; do
    response=$(curl -s -X POST "$PB_URL$endpoint" -H "Content-Type: application/json" -d "$AUTH_BODY")
    TOKEN=$(echo "$response" | jq -r '.token // empty')
    [[ -n "$TOKEN" ]] && break
done
if [[ -z "$TOKEN" ]]; then
    echo "Error: Authentication failed — check email/password."
    exit 1
fi
echo "  OK"

AUTH_HEADER="Authorization: Bearer $TOKEN"
TMPFILE=$(mktemp)
trap 'rm -f "$TMPFILE"' EXIT

# Returns the collection ID — all informational output goes to stderr so the
# caller captures only the bare ID via $(...).
get_or_create_collection() {
    local name=$1
    local body=$2

    # If collection already exists, return its ID without failing.
    local check_code
    check_code=$(curl -s -o "$TMPFILE" -w "%{http_code}" \
        -H "$AUTH_HEADER" "$PB_URL/api/collections/$name")
    if [[ "$check_code" == "200" ]]; then
        echo "→ '$name' already exists — using existing collection." >&2
        jq -r '.id' "$TMPFILE"
        return
    fi

    echo "→ Creating '$name' collection..." >&2
    local http_code
    http_code=$(curl -s -o "$TMPFILE" -w "%{http_code}" \
        -X POST "$PB_URL/api/collections" \
        -H "$AUTH_HEADER" \
        -H "Content-Type: application/json" \
        -d "$body")
    if [[ "$http_code" != 2* ]]; then
        echo "  Error (HTTP $http_code): $(jq -r '.message // .' "$TMPFILE")" >&2
        exit 1
    fi
    echo "  Created." >&2
    jq -r '.id' "$TMPFILE"
}

# ── matches ───────────────────────────────────────────────────────────────────
MATCHES_ID=$(get_or_create_collection "matches" '{
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
echo "  matches id: $MATCHES_ID"

# ── incidents ─────────────────────────────────────────────────────────────────
INCIDENTS_BODY=$(jq -n --arg cid "$MATCHES_ID" '{
  "name": "incidents",
  "type": "base",
  "schema": [
    {"name": "match_id",            "type": "relation", "required": true,  "options": {"collectionId": $cid, "cascadeDelete": true, "maxSelect": 1}},
    {"name": "minute",              "type": "number",   "required": false, "options": {}},
    {"name": "type",                "type": "text",     "required": false, "options": {}},
    {"name": "team",                "type": "text",     "required": false, "options": {}},
    {"name": "player_number",       "type": "text",     "required": false, "options": {}},
    {"name": "player_name",         "type": "text",     "required": false, "options": {}},
    {"name": "offence_description", "type": "text",     "required": false, "options": {}}
  ]
}')

INCIDENTS_ID=$(get_or_create_collection "incidents" "$INCIDENTS_BODY")
echo "  incidents id: $INCIDENTS_ID"

echo ""
echo "Done. Collections are live at $PB_URL/_/#/collections"
