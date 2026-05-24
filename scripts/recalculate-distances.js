#!/usr/bin/env node
// Recalculate total_distance_km for all PocketBase match records that have
// gps_track data, using correct haversine sums between consecutive points.

const PB_URL = process.env.PB_URL || 'http://192.168.1.106:8090'

function toRad(deg) { return deg * Math.PI / 180 }

function haversineKm(lat1, lng1, lat2, lng2) {
  const R = 6371
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

function calcTotalDistanceKm(points) {
  let total = 0
  for (let i = 1; i < points.length; i++) {
    total += haversineKm(
      points[i - 1].latitude, points[i - 1].longitude,
      points[i].latitude,     points[i].longitude,
    )
  }
  return total
}

async function fetchAllMatchesWithGps() {
  const records = []
  let page = 1
  while (true) {
    const res = await fetch(
      `${PB_URL}/api/collections/matches/records?perPage=100&page=${page}&filter=${encodeURIComponent("gps_track != ''")}`,
    )
    const data = await res.json()
    records.push(...data.items)
    if (page >= data.totalPages) break
    page++
  }
  return records
}

async function main() {
  console.log(`Connecting to PocketBase at ${PB_URL}`)
  const matches = await fetchAllMatchesWithGps()
  console.log(`Found ${matches.length} match(es) with GPS track data\n`)

  let updated = 0
  for (const m of matches) {
    let points
    try { points = JSON.parse(m.gps_track) } catch { continue }
    if (!Array.isArray(points) || points.length < 2) continue

    const corrected = calcTotalDistanceKm(points)
    const stored    = Number(m.total_distance_km) || 0
    const diff      = Math.abs(corrected - stored)

    process.stdout.write(
      `  ${m.id}  stored=${stored.toFixed(3)} km  corrected=${corrected.toFixed(3)} km  pts=${points.length}`,
    )

    if (diff < 0.001) { console.log('  (no change)'); continue }

    const res = await fetch(`${PB_URL}/api/collections/matches/records/${m.id}`, {
      method:  'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ total_distance_km: corrected }),
    })

    if (res.ok) { console.log('  ✓ updated'); updated++ }
    else        { console.log(`  ✗ error ${res.status}: ${await res.text()}`) }
  }

  console.log(`\nDone. ${updated}/${matches.length} record(s) updated.`)
}

main().catch(err => { console.error(err); process.exit(1) })
