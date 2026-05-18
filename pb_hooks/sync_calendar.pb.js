// Ref6 — iCal calendar sync hook  GET /api/cal/sync
// All logic is inlined inside the handler — PocketBase v0.19 Goja does not
// share module-level vars into the registered callback at invocation time.
// In PocketBase v0.19, the HTTP response body is in resp.raw (not resp.body).

routerAdd("GET", "/api/cal/sync", function(e) {

  // ── helpers ───────────────────────────────────────────────────────────────

  function pad2(n) { return n < 10 ? "0" + n : "" + n }

  function prop(block, name) {
    var re = new RegExp("(?:^|\\r?\\n)" + name + "(?:;[^:]+)?:([^\\r\\n]*)", "i")
    var m = block.match(re)
    return m ? m[1].trim() : ""
  }

  function parseDtstart(block) {
    var m = block.match(/(?:^|\r?\n)DTSTART(?:;[^:]+)?:([^\r\n]+)/i)
    if (!m) return { date: "", time: "" }
    var raw = m[1].trim()

    // Date-only value (8 chars, e.g. VALUE=DATE)
    if (raw.length <= 8) {
      return { date: raw.slice(0,4)+"-"+raw.slice(4,6)+"-"+raw.slice(6,8), time: "" }
    }

    var year  = parseInt(raw.slice(0,4), 10)
    var month = parseInt(raw.slice(4,6), 10) - 1  // 0-indexed for Date.UTC
    var day   = parseInt(raw.slice(6,8), 10)
    var hour  = parseInt(raw.slice(9,11), 10)
    var min   = parseInt(raw.slice(11,13), 10)
    var isUtc = raw.slice(-1) === "Z"

    if (isUtc) {
      // Convert UTC → Australia/Sydney (AEST = UTC+10; approximation, ignores DST)
      var utcMs = Date.UTC(year, month, day, hour, min)
      var local = new Date(utcMs + 10 * 3600000)
      return {
        date: local.getUTCFullYear() + "-" + pad2(local.getUTCMonth()+1) + "-" + pad2(local.getUTCDate()),
        time: pad2(local.getUTCHours()) + ":" + pad2(local.getUTCMinutes()),
      }
    }

    // TZID or floating — treat as local time
    return {
      date: raw.slice(0,4)+"-"+raw.slice(4,6)+"-"+raw.slice(6,8),
      time: raw.slice(9,11)+":"+raw.slice(11,13),
    }
  }

  function parseTeams(summary) {
    var s = summary.trim(), competition = ""
    var bm = s.match(/^\[([^\]]+)\]\s+(.+)$/) || s.match(/^\(([^)]+)\)\s+(.+)$/)
    if (bm) { competition = bm[1].trim(); s = bm[2].trim() }
    s = s.replace(/^(match|referee|ref\.?|appointment|game|fixture)\s*[:\-–]+\s*/i, "").trim()
    var vm = s.match(/^(.+?)\s+vs?\.?\s+(.+)$/i)
    if (vm) return { homeTeam: vm[1].trim(), awayTeam: vm[2].trim(), competition: competition }
    return { homeTeam: s, awayTeam: "", competition: competition }
  }

  function extractField(text, key) {
    var m = text.match(new RegExp(key + "\\s*:\\s*([^\\n]+)", "i"))
    return m ? m[1].trim() : ""
  }

  function extractVenue(location, description) {
    if (location) return location.split(",")[0].trim()
    return extractField(description, "venue") || extractField(description, "ground") || ""
  }

  // Detect PLM or PLR from event title and description.
  // PLR is checked before PLM since both share the "PL" prefix.
  // Legacy SPL/SPLR names are also recognised and mapped to the current codes.
  function detectAgeGroup(summary, desc) {
    var text = (summary + " " + desc).toUpperCase()
    if (text.indexOf("PLR")  !== -1) return "PLR"
    if (text.indexOf("PLM")  !== -1) return "PLM"
    if (text.indexOf("SPLR") !== -1) return "PLR"
    if (text.indexOf("SPL")  !== -1) return "PLM"
    return ""
  }

  // Detect State Cup: matches "STATE CUP" or word-boundary "SRC".
  function detectStateCup(summary, desc, competition) {
    var text = (summary + " " + desc + " " + competition).toUpperCase()
    if (text.indexOf("STATE CUP") !== -1) return true
    if (/(?:^|\W)SRC(?:\W|$)/.test(text)) return true
    return false
  }

  function parseVevent(block) {
    var uid     = prop(block, "UID")
    var summary = prop(block, "SUMMARY").replace(/\\,/g,",").replace(/\\n/g," ").replace(/\\;/g,";")
    var desc    = prop(block, "DESCRIPTION").replace(/\\n/g,"\n").replace(/\\,/g,",").replace(/\\;/g,";")
    var loc     = prop(block, "LOCATION").replace(/\\,/g,",").replace(/\\;/g,";")
    var dt      = parseDtstart(block)
    var teams   = parseTeams(summary)
    var comp    = teams.competition || extractField(desc,"competition") || extractField(desc,"league") || ""
    var ageGroup = detectAgeGroup(summary, desc)
    var stateCup = detectStateCup(summary, desc, comp)
    return { uid:uid, homeTeam:teams.homeTeam, awayTeam:teams.awayTeam,
             competition:comp, venue:extractVenue(loc,desc), date:dt.date, time:dt.time,
             ageGroup:ageGroup, stateCup:stateCup }
  }

  function parseIcal(text) {
    text = text.replace(/\r\n([ \t])/g,"$1").replace(/\n([ \t])/g,"$1")
    var events = [], parts = text.split("BEGIN:VEVENT")
    for (var i = 1; i < parts.length; i++) {
      var ev = parseVevent(parts[i].split("END:VEVENT")[0])
      if (ev) events.push(ev)
    }
    return events
  }

  // ── 1. Fetch iCal ─────────────────────────────────────────────────────────
  var ICAL_URL = "https://calendar.google.com/calendar/ical/sports%40luppos.com/private-4f88a817a4100138006eb0d6107d0ade/basic.ics"
  var resp
  try {
    resp = $http.send({ url: ICAL_URL, method: "GET", headers: { "User-Agent": "Ref6/1.0" }, timeout: 20 })
  } catch (fetchErr) {
    throw new BadRequestError("Could not reach calendar: " + fetchErr)
  }
  if (resp.statusCode !== 200) {
    throw new BadRequestError("Calendar returned HTTP " + resp.statusCode)
  }

  // ── 2. Parse (resp.raw is the body string in PocketBase v0.19) ────────────
  var bodyStr = resp.raw || String(resp.body) || ""
  var events
  try { events = parseIcal(bodyStr) }
  catch (parseErr) { throw new BadRequestError("Parse error: " + parseErr) }

  // ── 3. Filter to relevant date window (30 days ago → 12 months ahead) ────
  var now      = new Date()
  var earliest = new Date(now.getTime() - 30 * 86400000)
  var latest   = new Date(now.getTime() + 366 * 86400000)
  var filtered = []
  for (var fi = 0; fi < events.length; fi++) {
    var ev = events[fi]
    if (!ev.date) { filtered.push(ev); continue }  // no date → include
    var d = new Date(ev.date + "T00:00:00")
    if (d >= earliest && d <= latest) filtered.push(ev)
  }

  // ── 4. Upsert match_setups ────────────────────────────────────────────────
  var dao = $app.dao()
  var collection
  try { collection = dao.findCollectionByNameOrId("match_setups") }
  catch (colErr) { throw new BadRequestError("Collection error: " + colErr) }

  var created = 0, updated = 0, skipped = 0

  for (var i = 0; i < filtered.length; i++) {
    var ev = filtered[i]
    if (!ev.homeTeam || !ev.awayTeam) { skipped++; continue }

    var record = null
    if (ev.uid) {
      try {
        var r1 = dao.findRecordsByFilter("match_setups",
          "ical_uid = '" + ev.uid.replace(/'/g,"''") + "'", "", 1, 0)
        if (r1 && r1.length > 0) record = r1[0]
      } catch (_) {}
    }
    if (!record && ev.date) {
      try {
        var r2 = dao.findRecordsByFilter("match_setups",
          "kickoff_date='" + ev.date + "'" +
          "&&home_team='" + ev.homeTeam.replace(/'/g,"''") + "'" +
          "&&away_team='" + ev.awayTeam.replace(/'/g,"''") + "'",
          "", 1, 0)
        if (r2 && r2.length > 0) record = r2[0]
      } catch (_) {}
    }

    var isNew = !record
    if (!isNew) {
      var st = ""
      try { st = record.getString("status") } catch(_) { try { st = record.get("status") } catch(_) {} }
      st = st || ""
      if (st !== "" && st !== "pending") { skipped++; continue }
    } else {
      record = new Record(collection)
    }

    try { record.set("home_team", ev.homeTeam) } catch(_) {}
    try { record.set("away_team", ev.awayTeam) } catch(_) {}
    if (ev.date)        try { record.set("kickoff_date",  ev.date) }        catch(_) {}
    if (ev.time)        try { record.set("kickoff_time",  ev.time) }        catch(_) {}
    if (ev.venue)       try { record.set("venue",         ev.venue) }       catch(_) {}
    if (ev.competition) try { record.set("competition",   ev.competition) } catch(_) {}
    if (ev.uid)         try { record.set("ical_uid",      ev.uid) }         catch(_) {}

    // Apply detected age group (PLM/PLR) to both new and existing pending records
    if (ev.ageGroup) {
      try { record.set("age_group", ev.ageGroup) } catch(_) {}
      var isPremierLeague = ev.ageGroup === "PLM" || ev.ageGroup === "PLR"
      try { record.set("record_goal_scorers", isPremierLeague) } catch(_) {}
    }

    if (isNew) {
      try { record.set("status",           "pending") }  catch(_) {}
      try { record.set("half_length",      45) }          catch(_) {}
      try { record.set("two_yellows_rule", "red_card") }  catch(_) {}
      try { record.set("dissent_sin_bin",  true) }        catch(_) {}
      // record_goal_scorers already set above if age group detected; default to false for unknown
      if (!ev.ageGroup) {
        try { record.set("record_goal_scorers", false) } catch(_) {}
      }
    }

    // State Cup overrides: sin bin OFF, penalties ON, goal scorers ON
    if (ev.stateCup) {
      try { record.set("dissent_sin_bin",     false) } catch(_) {}
      try { record.set("penalties",           true)  } catch(_) {}
      try { record.set("record_goal_scorers", true)  } catch(_) {}
    }

    try {
      dao.saveRecord(record)
      if (isNew) { created++ } else { updated++ }
    } catch (saveErr) { skipped++ }
  }

  return e.json(200, { ok:true, total:events.length, window:filtered.length,
                       created:created, updated:updated, skipped:skipped })
})
