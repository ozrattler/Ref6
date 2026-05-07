export function kitStyle(hex) {
  if (!hex || !/^#[0-9a-f]{6}$/i.test(hex)) return {}
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const lum = (r * 299 + g * 587 + b * 114) / 1000
  return {
    color: hex,
    textShadow: lum > 160 ? '0 0 8px rgba(0,0,0,.95), 0 0 3px rgba(0,0,0,.8)' : undefined,
  }
}
