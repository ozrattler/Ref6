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

# Returns collection ID via stdout; all progress goes to stderr.
get_or_create_collection() {
    local name=$1 body=$2
    local check_code
    check_code=$(curl -s -o "$TMPFILE" -w "%{http_code}" -H "$AUTH_HEADER" "$PB_URL/api/collections/$name")
    if [[ "$check_code" == "200" ]]; then
        echo "→ '$name' already exists — using existing id." >&2
        jq -r '.id' "$TMPFILE"
        return
    fi
    echo "→ Creating '$name' collection..." >&2
    local http_code
    http_code=$(curl -s -o "$TMPFILE" -w "%{http_code}" \
        -X POST "$PB_URL/api/collections" \
        -H "$AUTH_HEADER" -H "Content-Type: application/json" -d "$body")
    if [[ "$http_code" != 2* ]]; then
        echo "  Error (HTTP $http_code): $(jq -r '.message // .' "$TMPFILE")" >&2
        exit 1
    fi
    echo "  Created." >&2
    jq -r '.id' "$TMPFILE"
}

# Add any missing fields to a collection. Args: cid label field_json ...
ensure_fields() {
    local cid=$1 label=$2
    shift 2
    echo "→ Checking '$label' schema..." >&2
    local col_data current new_schema
    col_data=$(curl -s -H "$AUTH_HEADER" "$PB_URL/api/collections/$cid")
    current=$(echo "$col_data" | jq '.schema // []')
    new_schema="$current"
    local added=0
    for field_json in "$@"; do
        local fname exists
        fname=$(echo "$field_json" | jq -r '.name')
        exists=$(echo "$new_schema" | jq --arg n "$fname" '[.[] | select(.name == $n)] | length')
        if [[ "$exists" == "0" ]]; then
            new_schema=$(echo "$new_schema" | jq --argjson f "$field_json" '. += [$f]')
            added=$((added + 1))
        fi
    done
    if [[ $added -gt 0 ]]; then
        local code
        code=$(curl -s -o "$TMPFILE" -w "%{http_code}" -X PATCH "$PB_URL/api/collections/$cid" \
            -H "$AUTH_HEADER" -H "Content-Type: application/json" \
            -d "{\"schema\": $new_schema}")
        if [[ "$code" == 2* ]]; then
            echo "  Added $added field(s)." >&2
        else
            echo "  Warning (HTTP $code): $(jq -r '.message // .' "$TMPFILE")" >&2
        fi
    else
        echo "  All fields present." >&2
    fi
}

# Sets all CRUD rules to "" (public) on a collection by id.
set_public_rules() {
    local cid=$1
    curl -s -o /dev/null -X PATCH "$PB_URL/api/collections/$cid" \
        -H "$AUTH_HEADER" -H "Content-Type: application/json" \
        -d '{"listRule":"","viewRule":"","createRule":"","updateRule":"","deleteRule":""}'
}

# Shared field definitions reused across collections
TEXT='{"required":false,"options":{}}'

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
ensure_fields "$MATCHES_ID" "matches" \
    '{"name":"referee",         "type":"text","required":false,"options":{}}' \
    '{"name":"ar1",             "type":"text","required":false,"options":{}}' \
    '{"name":"ar2",             "type":"text","required":false,"options":{}}' \
    '{"name":"fourth_official", "type":"text","required":false,"options":{}}' \
    '{"name":"home_colour",     "type":"text","required":false,"options":{}}' \
    '{"name":"away_colour",     "type":"text","required":false,"options":{}}' \
    '{"name":"venue",           "type":"text","required":false,"options":{}}' \
    '{"name":"kickoff_date",      "type":"text",  "required":false,"options":{}}' \
    '{"name":"kickoff_time",      "type":"text",  "required":false,"options":{}}' \
    '{"name":"gps_track",         "type":"text",  "required":false,"options":{}}' \
    '{"name":"total_distance_km", "type":"number","required":false,"options":{}}' \
    '{"name":"average_speed_kmh", "type":"number","required":false,"options":{}}' \
    '{"name":"max_speed_kmh",     "type":"number","required":false,"options":{}}' \
    '{"name":"avg_heart_rate",    "type":"number","required":false,"options":{}}' \
    '{"name":"max_heart_rate",    "type":"number","required":false,"options":{}}'
set_public_rules "$MATCHES_ID"
echo "  matches id: $MATCHES_ID"

# ── incidents ─────────────────────────────────────────────────────────────────
INCIDENTS_BODY=$(jq -n --arg cid "$MATCHES_ID" '{
  "name": "incidents",
  "type": "base",
  "schema": [
    {"name": "match_id",            "type": "relation", "required": true,  "options": {"collectionId": $cid, "cascadeDelete": true, "maxSelect": 1}},
    {"name": "half",                "type": "number",   "required": false, "options": {}},
    {"name": "minute",              "type": "number",   "required": false, "options": {}},
    {"name": "type",                "type": "text",     "required": false, "options": {}},
    {"name": "team",                "type": "text",     "required": false, "options": {}},
    {"name": "player_number",       "type": "text",     "required": false, "options": {}},
    {"name": "player_name",         "type": "text",     "required": false, "options": {}},
    {"name": "offence_description", "type": "text",     "required": false, "options": {}}
  ]
}')
INCIDENTS_ID=$(get_or_create_collection "incidents" "$INCIDENTS_BODY")
ensure_fields "$INCIDENTS_ID" "incidents" \
    '{"name":"half",      "type":"number","required":false,"options":{}}' \
    '{"name":"goal_type", "type":"text",  "required":false,"options":{}}' \
    '{"name":"latitude",  "type":"number","required":false,"options":{}}' \
    '{"name":"longitude", "type":"number","required":false,"options":{}}'
set_public_rules "$INCIDENTS_ID"
echo "  incidents id: $INCIDENTS_ID"

# ── match_setups ──────────────────────────────────────────────────────────────
MATCH_SETUPS_ID=$(get_or_create_collection "match_setups" '{
  "name": "match_setups",
  "type": "base",
  "schema": [
    {"name": "competition",      "type": "text",   "required": false, "options": {}},
    {"name": "home_team",        "type": "text",   "required": true,  "options": {}},
    {"name": "away_team",        "type": "text",   "required": true,  "options": {}},
    {"name": "age_group",        "type": "text",   "required": false, "options": {}},
    {"name": "half_length",      "type": "number", "required": false, "options": {}},
    {"name": "two_yellows_rule", "type": "text",   "required": false, "options": {}},
    {"name": "dissent_sin_bin",  "type": "bool",   "required": false, "options": {}},
    {"name": "status",           "type": "text",   "required": false, "options": {}}
  ]
}')
ensure_fields "$MATCH_SETUPS_ID" "match_setups" \
    '{"name":"referee",              "type":"text","required":false,"options":{}}' \
    '{"name":"ar1",                  "type":"text","required":false,"options":{}}' \
    '{"name":"ar2",                  "type":"text","required":false,"options":{}}' \
    '{"name":"fourth_official",      "type":"text","required":false,"options":{}}' \
    '{"name":"home_colour",          "type":"text","required":false,"options":{}}' \
    '{"name":"away_colour",          "type":"text","required":false,"options":{}}' \
    '{"name":"venue",                "type":"text","required":false,"options":{}}' \
    '{"name":"kickoff_date",         "type":"text","required":false,"options":{}}' \
    '{"name":"kickoff_time",         "type":"text","required":false,"options":{}}' \
    '{"name":"two_yellows_rule",     "type":"text","required":false,"options":{}}' \
    '{"name":"dissent_sin_bin",      "type":"bool","required":false,"options":{}}' \
    '{"name":"record_goal_scorers",  "type":"bool","required":false,"options":{}}' \
    '{"name":"extra_time",           "type":"bool","required":false,"options":{}}' \
    '{"name":"penalties",            "type":"bool","required":false,"options":{}}' \
    '{"name":"ical_uid",             "type":"text","required":false,"options":{}}'
set_public_rules "$MATCH_SETUPS_ID"
echo "  match_setups id: $MATCH_SETUPS_ID"

# ── templates ─────────────────────────────────────────────────────────────────
TEMPLATES_ID=$(get_or_create_collection "templates" '{
  "name": "templates",
  "type": "base",
  "schema": [
    {"name": "name",                "type": "text",   "required": true,  "options": {}},
    {"name": "competition",         "type": "text",   "required": false, "options": {}},
    {"name": "age_group",           "type": "text",   "required": false, "options": {}},
    {"name": "half_length",         "type": "number", "required": false, "options": {}},
    {"name": "two_yellows_rule",    "type": "text",   "required": false, "options": {}},
    {"name": "dissent_sin_bin",     "type": "bool",   "required": false, "options": {}},
    {"name": "record_goal_scorers", "type": "bool",   "required": false, "options": {}},
    {"name": "extra_time",          "type": "bool",   "required": false, "options": {}},
    {"name": "penalties",           "type": "bool",   "required": false, "options": {}},
    {"name": "referee",             "type": "text",   "required": false, "options": {}},
    {"name": "ar1",                 "type": "text",   "required": false, "options": {}},
    {"name": "ar2",                 "type": "text",   "required": false, "options": {}},
    {"name": "fourth_official",     "type": "text",   "required": false, "options": {}}
  ]
}')
ensure_fields "$TEMPLATES_ID" "templates" \
    '{"name":"record_goal_scorers",  "type":"bool","required":false,"options":{}}' \
    '{"name":"extra_time",           "type":"bool","required":false,"options":{}}' \
    '{"name":"penalties",            "type":"bool","required":false,"options":{}}'
set_public_rules "$TEMPLATES_ID"
echo "  templates id: $TEMPLATES_ID"

echo ""
echo "All done. Collections are live at $PB_URL/_/#/collections"
