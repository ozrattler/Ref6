const R = 6371 // Earth radius km

function toRad(deg) { return deg * Math.PI / 180 }

export function haversineKm(lat1, lng1, lat2, lng2) {
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

// Sum haversine distances between consecutive GPS track points.
// Each point must have { latitude, longitude } fields.
export function calcTotalDistanceKm(points) {
  let total = 0
  for (let i = 1; i < points.length; i++) {
    total += haversineKm(
      points[i - 1].latitude, points[i - 1].longitude,
      points[i].latitude,     points[i].longitude,
    )
  }
  return total
}
