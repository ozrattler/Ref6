'use strict';

// ═══════════════════════════════════════════════════════════
// LAWS OF THE GAME – OFFENCE LABELS
// ═══════════════════════════════════════════════════════════

const YC_LABELS = {
  USB:   'Unsporting behaviour',
  DWA:   'Dissent by word or action',
  PO:    'Persistent offences',
  DRTP:  'Delaying restart of play',
  FTRRD: 'Failure to respect required distance',
  EFP:   'Entering/leaving field without permission',
  ERRA:  'Entering referee review area',
  ECG:   'Excessive goal celebration',
};

const RC_LABELS = {
  SFP:      'Serious foul play',
  VC:       'Violent conduct',
  SAP:      'Spitting at a person',
  BAP:      'Biting a person',
  'DOGSO-H':'DOGSO – handball',
  'DOGSO-O':'DOGSO – other offence',
  OIAG:     'Offensive/insulting/abusive language or gestures',
  '2YC':    'Second yellow card',
};

// ═══════════════════════════════════════════════════════════
// STATE
// ═══════════════════════════════════════════════════════════

const DEFAULT_STATE = {
  competition: '',
  homeTeam: 'Home',
  awayTeam: 'Away',
  halfLength: 45,
  sinBinEnabled: false,
  sinBinDuration: 10,

  // 'setup' | 'first-half' | 'half-time' | 'second-half' | 'full-time'
  phase: 'setup',

  // Timer: tracks seconds elapsed within the *current half*
  timerOffset: 0,
  timerStart: null,  // Date.now() when last unpaused; null = paused

  // Absolute seconds at half time whistle (used for second half display base)
  firstHalfSeconds: 0,

  homeScore: 0,
  awayScore: 0,

  events: [],       // { id, type, team, half, halfSeconds, player, offence, sinBinDuration, sinBinExpiry }
  nextId: 1,
};

let state = loadState() || deepClone(DEFAULT_STATE);

// ═══════════════════════════════════════════════════════════
// PERSISTENCE
// ═══════════════════════════════════════════════════════════

function saveState() {
  // Snapshot timerOffset including time since last start
  const snap = deepClone(state);
  if (snap.timerStart !== null) {
    snap.timerOffset = getHalfSeconds();
    snap.timerStart = null;  // will restart on load
  }
  try { localStorage.setItem('refsix_state', JSON.stringify(snap)); } catch (_) {}
}

function loadState() {
  try {
    const raw = localStorage.getItem('refsix_state');
    if (!raw) return null;
    const s = JSON.parse(raw);
    // Don't auto-resume the timer after page load/refresh — safer for the ref
    s.timerStart = null;
    return s;
  } catch (_) { return null; }
}

function clearSavedState() {
  try { localStorage.removeItem('refsix_state'); } catch (_) {}
}

function deepClone(obj) { return JSON.parse(JSON.stringify(obj)); }

// ═══════════════════════════════════════════════════════════
// TIMER
// ═══════════════════════════════════════════════════════════

let _tickInterval = null;

function getHalfSeconds() {
  if (state.timerStart === null) return state.timerOffset;
  return state.timerOffset + Math.floor((Date.now() - state.timerStart) / 1000);
}

// Returns display-ready match clock in seconds
// First half: 0 – ∞  |  Second half: halfLength*60 – ∞
function getMatchSeconds() {
  const half = getHalfSeconds();
  return state.phase === 'second-half'
    ? state.halfLength * 60 + half
    : half;
}

function startClock() {
  if (state.timerStart !== null) return;
  state.timerStart = Date.now();
  _tickInterval = setInterval(onTick, 500);
  updatePauseBtn();
  saveState();
}

function pauseClock() {
  if (state.timerStart === null) return;
  state.timerOffset = getHalfSeconds();
  state.timerStart = null;
  clearInterval(_tickInterval);
  _tickInterval = null;
  updatePauseBtn();
  saveState();
}

function toggleClock() {
  if (state.timerStart === null) startClock(); else pauseClock();
}

// ── Tick ──────────────────────────────────────────────────

let _halfEndAlerted = false;

function onTick() {
  const ms = getMatchSeconds();
  renderTimerDisplay(ms);
  renderSinBins(ms);
  checkHalfTimeAlert(ms);
}

function checkHalfTimeAlert(matchSecs) {
  const threshold = state.phase === 'first-half'
    ? state.halfLength * 60
    : state.halfLength * 120;

  if (!_halfEndAlerted && matchSecs >= threshold) {
    _halfEndAlerted = true;
    beep();
  }
}

// ── Display helpers ────────────────────────────────────────

function pad2(n) { return String(n).padStart(2, '0'); }

function formatClock(totalSecs) {
  const m = Math.floor(totalSecs / 60);
  const s = totalSecs % 60;
  return `${pad2(m)}:${pad2(s)}`;
}

// e.g. "45+1'" or "87'" – used in event labels
function matchTimeStr(half, halfSecs, halfLength) {
  const baseMins = half === 2 ? halfLength : 0;
  const scheduledSecs = halfLength * 60;
  if (halfSecs < scheduledSecs) {
    return `${baseMins + Math.floor(halfSecs / 60) + 1}'`;
  }
  const extra = Math.ceil((halfSecs - scheduledSecs) / 60);
  return `${baseMins + halfLength}+${extra}'`;
}

function renderTimerDisplay(matchSecs) {
  const scheduledMatchSecs = state.phase === 'second-half'
    ? state.halfLength * 120
    : state.halfLength * 60;

  const isAT = matchSecs >= scheduledMatchSecs;

  el('timer-main').textContent = formatClock(matchSecs);
  el('timer-at').classList.toggle('hidden', !isAT);

  if (isAT) {
    const atSecs = matchSecs - scheduledMatchSecs;
    const atMins = Math.ceil(atSecs / 60);
    const baseMins = state.phase === 'second-half'
      ? state.halfLength * 2
      : state.halfLength;
    el('timer-extra').textContent = `${baseMins}+${atMins}'`;
    el('timer-extra').classList.remove('hidden');
  } else {
    el('timer-extra').classList.add('hidden');
  }
}

// ═══════════════════════════════════════════════════════════
// AUDIO
// ═══════════════════════════════════════════════════════════

function beep() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain); gain.connect(ctx.destination);
    osc.type = 'square';
    osc.frequency.setValueAtTime(880, ctx.currentTime);
    gain.gain.setValueAtTime(0.3, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.6);
    osc.start(); osc.stop(ctx.currentTime + 0.6);
  } catch (_) {}
}

// ═══════════════════════════════════════════════════════════
// EVENTS
// ═══════════════════════════════════════════════════════════

function addEvent(type, team, player, offence, sinBinDuration) {
  const halfNum  = state.phase === 'second-half' ? 2 : 1;
  const halfSecs = getHalfSeconds();
  const matchSecs = getMatchSeconds();

  const evt = {
    id:             state.nextId++,
    type,           // 'goal' | 'yellow' | 'red' | 'sinbin'
    team,           // 'home' | 'away'
    half:           halfNum,
    halfSecs,
    matchSecs,
    player:         player || '',
    offence:        offence || '',
    sinBinDuration: sinBinDuration || 0,
    // Absolute match-seconds when sin bin expires
    sinBinExpiry:   type === 'sinbin' ? matchSecs + sinBinDuration * 60 : null,
  };

  if (type === 'goal') {
    if (team === 'home') state.homeScore++;
    else state.awayScore++;
  }

  state.events.push(evt);
  saveState();
  renderMatch();
  showToast(eventToastMsg(evt));
}

function undoLastEvent() {
  if (!state.events.length) { showToast('Nothing to undo'); return; }
  const evt = state.events.pop();
  if (evt.type === 'goal') {
    if (evt.team === 'home') state.homeScore = Math.max(0, state.homeScore - 1);
    else state.awayScore = Math.max(0, state.awayScore - 1);
  }
  state.nextId--;
  saveState();
  renderMatch();
  showToast('Undone');
}

function eventToastMsg(evt) {
  const team = evt.team === 'home' ? state.homeTeam : state.awayTeam;
  const who  = evt.player ? ` – ${evt.player}` : '';
  if (evt.type === 'goal')    return `⚽ Goal – ${team}${who}`;
  if (evt.type === 'yellow')  return `🟨 Yellow – ${team}${who}`;
  if (evt.type === 'red')     return `🟥 Red – ${team}${who}`;
  if (evt.type === 'sinbin')  return `⏱ Sin bin – ${team}${who}`;
  return '';
}

// ═══════════════════════════════════════════════════════════
// SIN BINS
// ═══════════════════════════════════════════════════════════

let _expiredSinBins = new Set();

function activeSinBins(matchSecs) {
  return state.events.filter(e =>
    e.type === 'sinbin' &&
    e.sinBinExpiry !== null &&
    matchSecs < e.sinBinExpiry &&
    !_expiredSinBins.has(e.id)
  );
}

function renderSinBins(matchSecs) {
  const active = activeSinBins(matchSecs);

  // Detect newly expired
  state.events.filter(e => e.type === 'sinbin').forEach(e => {
    if (e.sinBinExpiry !== null && matchSecs >= e.sinBinExpiry && !_expiredSinBins.has(e.id)) {
      _expiredSinBins.add(e.id);
      const team = e.team === 'home' ? state.homeTeam : state.awayTeam;
      const who  = e.player ? ` (${e.player})` : '';
      showToast(`⏱ Sin bin over – ${team}${who} can return`);
      beep();
    }
  });

  const area = el('sinbin-area');
  const list = el('sinbin-list');

  if (!active.length) { area.classList.add('hidden'); return; }

  area.classList.remove('hidden');
  list.innerHTML = active.map(e => {
    const rem = Math.max(0, e.sinBinExpiry - matchSecs);
    const m   = Math.floor(rem / 60);
    const s   = rem % 60;
    const team = e.team === 'home' ? state.homeTeam : state.awayTeam;
    const warn = rem <= 60;
    return `<div class="sinbin-item">
      <div>
        <div class="sinbin-item-team">${escHtml(team)}</div>
        <div class="sinbin-item-info">${e.player ? escHtml(e.player) : 'Player'}</div>
      </div>
      <div class="sinbin-countdown${warn ? ' warning' : ''}">${pad2(m)}:${pad2(s)}</div>
    </div>`;
  }).join('');
}

// ═══════════════════════════════════════════════════════════
// VIEWS
// ═══════════════════════════════════════════════════════════

function showView(id) {
  document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
  el(id).classList.add('active');
}

// ── Setup ──────────────────────────────────────────────────

function renderSetup() {
  showView('view-setup');
}

// ── Match ──────────────────────────────────────────────────

function renderMatch() {
  // Team names
  const names = [
    ['match-competition-label', state.competition],
    ['match-half-label', state.phase === 'second-half' ? '2nd Half' : '1st Half'],
    ['sb-home-name', state.homeTeam],
    ['sb-away-name', state.awayTeam],
    ['sb-home-score', state.homeScore],
    ['sb-away-score', state.awayScore],
    ['ag-goal-home', state.homeTeam],
    ['ag-goal-away', state.awayTeam],
    ['ag-yc-home',   state.homeTeam],
    ['ag-yc-away',   state.awayTeam],
    ['ag-rc-home',   state.homeTeam],
    ['ag-rc-away',   state.awayTeam],
    ['ag-sb-home',   state.homeTeam],
    ['ag-sb-away',   state.awayTeam],
  ];
  names.forEach(([id, val]) => { const e = el(id); if (e) e.textContent = val; });

  // Sin bin buttons visibility
  el('ag-sb-home-btn').classList.toggle('hidden', !state.sinBinEnabled);
  el('ag-sb-away-btn').classList.toggle('hidden', !state.sinBinEnabled);

  // End half button label
  el('btn-end-half').textContent = state.phase === 'second-half'
    ? '⏸⏸ FULL TIME'
    : '⏸⏸ HALF TIME';

  // Event feed (last 3 events)
  const feed = el('event-feed');
  const recent = state.events.slice(-3).reverse();
  feed.innerHTML = recent.map(e => feedEventHtml(e)).join('');

  // Timer display from current state
  renderTimerDisplay(getMatchSeconds());

  // Sin bins
  renderSinBins(getMatchSeconds());

  updatePauseBtn();
}

function feedEventHtml(evt) {
  const team   = evt.team === 'home' ? state.homeTeam : state.awayTeam;
  const timeStr = matchTimeStr(evt.half, evt.halfSecs, state.halfLength);
  let icon = '';
  if (evt.type === 'goal')   icon = '<span class="feed-icon">&#9917;</span>';
  if (evt.type === 'yellow') icon = '<span class="feed-icon" style="font-size:14px">&#128280;</span>';
  if (evt.type === 'red')    icon = '<span class="feed-icon" style="font-size:14px">&#128308;</span>';
  if (evt.type === 'sinbin') icon = '<span class="feed-icon">&#8987;</span>';

  const who = evt.player ? ` ${escHtml(evt.player)}` : '';
  return `<div class="feed-event">${icon}
    <span class="feed-time">${timeStr}</span>
    <span class="feed-detail">${escHtml(team)}${who}</span>
  </div>`;
}

function updatePauseBtn() {
  const btn = el('btn-pause');
  const running = state.timerStart !== null;
  if (running) {
    btn.textContent = '⏸⏸ PAUSE';
    btn.classList.add('running');
  } else {
    btn.textContent = '▶ RESUME';
    btn.classList.remove('running');
  }
}

// ── Half time ─────────────────────────────────────────────

function renderHalftime() {
  el('ht-competition').textContent = state.competition;
  el('ht-home-name').textContent   = state.homeTeam;
  el('ht-away-name').textContent   = state.awayTeam;
  el('ht-home-score').textContent  = state.homeScore;
  el('ht-away-score').textContent  = state.awayScore;
  el('ht-time-played').textContent = `First half: ${formatClock(state.firstHalfSeconds)}`;

  const firstHalfEvents = state.events.filter(e => e.half === 1);
  el('ht-events').innerHTML = firstHalfEvents.length
    ? firstHalfEvents.map(e => eventItemHtml(e)).join('')
    : '<div class="empty-events">No events in first half</div>';
}

// ── Full time ─────────────────────────────────────────────

function renderFulltime() {
  el('ft-competition').textContent  = state.competition;
  el('ft-home-name').textContent    = state.homeTeam;
  el('ft-away-name').textContent    = state.awayTeam;
  el('ft-home-score').textContent   = state.homeScore;
  el('ft-away-score').textContent   = state.awayScore;

  const totalSecs = state.halfLength * 60 + getHalfSeconds();
  el('ft-match-info').textContent = `Match duration: ${formatClock(totalSecs)}`;

  // Build report body
  const body = el('ft-body');
  body.innerHTML = '';

  // Stats grid
  const homeYC = state.events.filter(e => (e.type === 'yellow' || e.type === 'sinbin') && e.team === 'home').length;
  const awayYC = state.events.filter(e => (e.type === 'yellow' || e.type === 'sinbin') && e.team === 'away').length;
  const homeRC = state.events.filter(e => e.type === 'red' && e.team === 'home').length;
  const awayRC = state.events.filter(e => e.type === 'red' && e.team === 'away').length;
  const homeSB = state.events.filter(e => e.type === 'sinbin' && e.team === 'home').length;
  const awaySB = state.events.filter(e => e.type === 'sinbin' && e.team === 'away').length;

  body.insertAdjacentHTML('beforeend', `
    <h3 class="summary-section-title">Match Statistics</h3>
    <div class="stats-grid">
      <div class="stat-row">
        <div class="stat-home">${state.homeScore}</div>
        <div class="stat-label">Goals</div>
        <div class="stat-away">${state.awayScore}</div>
      </div>
      <div class="stat-row">
        <div class="stat-home">${homeYC}</div>
        <div class="stat-label">Yellow Cards</div>
        <div class="stat-away">${awayYC}</div>
      </div>
      <div class="stat-row">
        <div class="stat-home">${homeRC}</div>
        <div class="stat-label">Red Cards</div>
        <div class="stat-away">${awayRC}</div>
      </div>
      ${state.sinBinEnabled ? `
      <div class="stat-row">
        <div class="stat-home">${homeSB}</div>
        <div class="stat-label">Sin Bins</div>
        <div class="stat-away">${awaySB}</div>
      </div>` : ''}
    </div>
  `);

  // First half events
  body.insertAdjacentHTML('beforeend', '<h3 class="summary-section-title">First Half</h3>');
  const h1evts = state.events.filter(e => e.half === 1);
  body.insertAdjacentHTML('beforeend',
    `<div class="event-list">${
      h1evts.length
        ? h1evts.map(e => eventItemHtml(e)).join('')
        : '<div class="empty-events">No events</div>'
    }</div>`
  );

  // Second half events
  body.insertAdjacentHTML('beforeend', '<h3 class="summary-section-title">Second Half</h3>');
  const h2evts = state.events.filter(e => e.half === 2);
  body.insertAdjacentHTML('beforeend',
    `<div class="event-list">${
      h2evts.length
        ? h2evts.map(e => eventItemHtml(e)).join('')
        : '<div class="empty-events">No events</div>'
    }</div>`
  );
}

function eventItemHtml(evt) {
  const team    = evt.team === 'home' ? state.homeTeam : state.awayTeam;
  const timeStr = matchTimeStr(evt.half, evt.halfSecs, state.halfLength);
  const player  = evt.player || '';

  let iconHtml = '';
  let desc     = '';
  let detail   = '';

  if (evt.type === 'goal') {
    iconHtml = '<span class="ev-icon">&#9917;</span>';
    desc     = `Goal – ${escHtml(team)}`;
    detail   = player ? `Scorer: ${escHtml(player)}` : '';
  } else if (evt.type === 'yellow') {
    iconHtml = '<div class="ev-card yc"></div>';
    desc     = `Yellow Card – ${escHtml(team)}`;
    detail   = [player && `Player: ${escHtml(player)}`, evt.offence && `${evt.offence}: ${YC_LABELS[evt.offence] || evt.offence}`].filter(Boolean).join(' · ');
  } else if (evt.type === 'red') {
    iconHtml = '<div class="ev-card rc"></div>';
    desc     = `Red Card – ${escHtml(team)}`;
    detail   = [player && `Player: ${escHtml(player)}`, evt.offence && `${evt.offence}: ${RC_LABELS[evt.offence] || evt.offence}`].filter(Boolean).join(' · ');
  } else if (evt.type === 'sinbin') {
    iconHtml = '<div class="ev-card sb"></div>';
    desc     = `Sin Bin (${evt.sinBinDuration}min) – ${escHtml(team)}`;
    detail   = [player && `Player: ${escHtml(player)}`, evt.offence && `${evt.offence}: ${YC_LABELS[evt.offence] || evt.offence}`].filter(Boolean).join(' · ');
  }

  return `<div class="event-item">
    ${iconHtml}
    <div class="ev-time">${timeStr}</div>
    <div class="ev-body">
      <div class="ev-desc">${desc}</div>
      ${detail ? `<div class="ev-detail">${detail}</div>` : ''}
    </div>
  </div>`;
}

// ═══════════════════════════════════════════════════════════
// MODALS
// ═══════════════════════════════════════════════════════════

let _modalTeam = null;

function openModal(type, team) {
  _modalTeam = team;
  const teamName = team === 'home' ? state.homeTeam : state.awayTeam;
  const overlay  = el('modal-overlay');

  overlay.querySelectorAll('.modal').forEach(m => m.classList.add('hidden'));
  overlay.classList.remove('hidden');

  if (type === 'goal') {
    el('mg-team-display').textContent = teamName;
    el('mg-player').value = '';
    el('modal-goal').classList.remove('hidden');
    setTimeout(() => el('mg-player').focus(), 100);

  } else if (type === 'yellow') {
    el('myc-team-display').textContent = teamName;
    el('myc-player').value = '';
    el('myc-offence').value = '';
    el('modal-yellow').classList.remove('hidden');
    setTimeout(() => el('myc-player').focus(), 100);

  } else if (type === 'red') {
    el('mrc-team-display').textContent = teamName;
    el('mrc-player').value = '';
    el('mrc-offence').value = '';
    el('modal-red').classList.remove('hidden');
    setTimeout(() => el('mrc-player').focus(), 100);

  } else if (type === 'sinbin') {
    el('msb-team-display').textContent = teamName;
    el('msb-player').value = '';
    el('msb-offence').value = '';
    el('msb-duration').value = state.sinBinDuration;
    el('msb-duration-val').textContent = state.sinBinDuration;
    el('modal-sinbin').classList.remove('hidden');
    setTimeout(() => el('msb-player').focus(), 100);
  }
}

function closeModal() {
  el('modal-overlay').classList.add('hidden');
  el('modal-overlay').querySelectorAll('.modal').forEach(m => m.classList.add('hidden'));
  _modalTeam = null;
}

function confirmGoal() {
  const player = el('mg-player').value.trim();
  addEvent('goal', _modalTeam, player, '', 0);
  closeModal();
}

function confirmYellow() {
  const offence = el('myc-offence').value;
  if (!offence) { el('myc-offence').focus(); return; }
  const player = el('myc-player').value.trim();
  addEvent('yellow', _modalTeam, player, offence, 0);
  closeModal();
}

function confirmRed() {
  const offence = el('mrc-offence').value;
  if (!offence) { el('mrc-offence').focus(); return; }
  const player = el('mrc-player').value.trim();
  addEvent('red', _modalTeam, player, offence, 0);
  closeModal();
}

function confirmSinBin() {
  const offence = el('msb-offence').value;
  if (!offence) { el('msb-offence').focus(); return; }
  const player   = el('msb-player').value.trim();
  const duration = parseInt(el('msb-duration').value, 10);
  addEvent('sinbin', _modalTeam, player, offence, duration);
  closeModal();
}

// ═══════════════════════════════════════════════════════════
// PHASE TRANSITIONS
// ═══════════════════════════════════════════════════════════

function startMatch() {
  state.competition  = el('inp-competition').value.trim();
  state.homeTeam     = el('inp-home').value.trim() || 'Home';
  state.awayTeam     = el('inp-away').value.trim() || 'Away';
  state.halfLength   = parseInt(el('inp-half-length').value, 10) || 45;
  state.sinBinEnabled = el('inp-sinbin-enabled').checked;
  state.sinBinDuration = parseInt(el('inp-sinbin-duration').value, 10) || 10;

  state.phase         = 'first-half';
  state.timerOffset   = 0;
  state.timerStart    = null;
  state.homeScore     = 0;
  state.awayScore     = 0;
  state.events        = [];
  state.nextId        = 1;
  state.firstHalfSeconds = 0;
  _expiredSinBins     = new Set();
  _halfEndAlerted     = false;

  saveState();
  showView('view-match');
  renderMatch();
  startClock();
}

function endHalf() {
  if (state.phase === 'first-half') {
    pauseClock();
    state.firstHalfSeconds = state.timerOffset;
    state.phase = 'half-time';
    saveState();
    showView('view-halftime');
    renderHalftime();
  } else if (state.phase === 'second-half') {
    pauseClock();
    state.phase = 'full-time';
    saveState();
    showView('view-fulltime');
    renderFulltime();
  }
}

function startSecondHalf() {
  state.phase       = 'second-half';
  state.timerOffset = 0;
  state.timerStart  = null;
  _halfEndAlerted   = false;
  saveState();
  showView('view-match');
  renderMatch();
  startClock();
}

function newMatch() {
  pauseClock();
  clearSavedState();
  state = deepClone(DEFAULT_STATE);
  _expiredSinBins = new Set();
  _halfEndAlerted = false;
  showView('view-setup');
}

// ═══════════════════════════════════════════════════════════
// SHARE / REPORT
// ═══════════════════════════════════════════════════════════

function buildReportText() {
  const lines = ['REFSIX MATCH REPORT', '=================='];
  if (state.competition) lines.push(state.competition);
  lines.push('');
  lines.push(`${state.homeTeam}  ${state.homeScore} – ${state.awayScore}  ${state.awayTeam}`);
  lines.push('');

  const homeYC = state.events.filter(e => (e.type === 'yellow' || e.type === 'sinbin') && e.team === 'home').length;
  const awayYC = state.events.filter(e => (e.type === 'yellow' || e.type === 'sinbin') && e.team === 'away').length;
  const homeRC = state.events.filter(e => e.type === 'red' && e.team === 'home').length;
  const awayRC = state.events.filter(e => e.type === 'red' && e.team === 'away').length;

  lines.push(`Yellow cards: ${state.homeTeam} ${homeYC} – ${awayYC} ${state.awayTeam}`);
  lines.push(`Red cards:    ${state.homeTeam} ${homeRC} – ${awayRC} ${state.awayTeam}`);
  lines.push('');
  lines.push('EVENTS');
  lines.push('------');

  if (!state.events.length) {
    lines.push('No events recorded');
  } else {
    state.events.forEach(evt => {
      const team    = evt.team === 'home' ? state.homeTeam : state.awayTeam;
      const timeStr = matchTimeStr(evt.half, evt.halfSecs, state.halfLength);
      const player  = evt.player ? ` (${evt.player})` : '';
      const labels  = { goal: 'GOAL', yellow: 'YELLOW', red: 'RED', sinbin: 'SIN BIN' };
      const offDesc = evt.offence
        ? ` – ${evt.offence}`
        : '';
      lines.push(`${timeStr.padEnd(8)} ${labels[evt.type] || evt.type} – ${team}${player}${offDesc}`);
    });
  }

  lines.push('');
  lines.push(`Generated by RefSix`);
  return lines.join('\n');
}

function shareReport() {
  const text = buildReportText();
  if (navigator.share) {
    navigator.share({ title: 'RefSix Match Report', text }).catch(() => copyToClipboard(text));
  } else {
    copyToClipboard(text);
  }
}

function copyToClipboard(text) {
  navigator.clipboard.writeText(text)
    .then(() => showToast('Report copied to clipboard'))
    .catch(() => showToast('Could not copy – try long-press'));
}

// ═══════════════════════════════════════════════════════════
// TOAST
// ═══════════════════════════════════════════════════════════

let _toastTimer = null;

function showToast(msg) {
  const t = el('toast');
  t.textContent = msg;
  t.classList.remove('hidden', 'fade-out');
  clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => {
    t.classList.add('fade-out');
    setTimeout(() => t.classList.add('hidden'), 300);
  }, 2200);
}

// ═══════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════

function el(id) { return document.getElementById(id); }

function escHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// ═══════════════════════════════════════════════════════════
// EVENT LISTENERS
// ═══════════════════════════════════════════════════════════

function bindEvents() {
  // Setup
  el('inp-half-length').addEventListener('input', () => {
    el('half-length-val').textContent = el('inp-half-length').value;
  });
  el('inp-sinbin-duration').addEventListener('input', () => {
    el('sinbin-duration-val').textContent = el('inp-sinbin-duration').value;
  });
  el('inp-sinbin-enabled').addEventListener('change', () => {
    el('sinbin-duration-group').classList.toggle('hidden', !el('inp-sinbin-enabled').checked);
  });
  el('btn-kickoff').addEventListener('click', startMatch);

  // Match controls
  el('btn-pause').addEventListener('click', toggleClock);
  el('btn-undo').addEventListener('click', undoLastEvent);
  el('btn-end-half').addEventListener('click', endHalf);

  // Action grid – event delegation
  el('action-grid').addEventListener('click', e => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;
    openModal(btn.dataset.action, btn.dataset.team);
  });

  // Goal modal
  el('mg-cancel').addEventListener('click', closeModal);
  el('mg-confirm').addEventListener('click', confirmGoal);
  el('mg-player').addEventListener('keydown', e => { if (e.key === 'Enter') confirmGoal(); });

  // Yellow modal
  el('myc-cancel').addEventListener('click', closeModal);
  el('myc-confirm').addEventListener('click', confirmYellow);

  // Red modal
  el('mrc-cancel').addEventListener('click', closeModal);
  el('mrc-confirm').addEventListener('click', confirmRed);

  // Sin bin modal
  el('msb-cancel').addEventListener('click', closeModal);
  el('msb-confirm').addEventListener('click', confirmSinBin);
  el('msb-duration').addEventListener('input', () => {
    el('msb-duration-val').textContent = el('msb-duration').value;
  });

  // Close modal on overlay backdrop tap
  el('modal-overlay').addEventListener('click', e => {
    if (e.target === el('modal-overlay')) closeModal();
  });

  // Half time / full time
  el('btn-start-second').addEventListener('click', startSecondHalf);
  el('btn-new-match').addEventListener('click', newMatch);
  el('btn-share').addEventListener('click', shareReport);
}

// ═══════════════════════════════════════════════════════════
// INIT
// ═══════════════════════════════════════════════════════════

function init() {
  // Register service worker
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }

  bindEvents();

  // Restore from saved state
  if (state.phase === 'setup' || !state.phase) {
    showView('view-setup');
  } else if (state.phase === 'first-half' || state.phase === 'second-half') {
    showView('view-match');
    renderMatch();
    // Don't auto-start timer – let ref tap resume
  } else if (state.phase === 'half-time') {
    showView('view-halftime');
    renderHalftime();
  } else if (state.phase === 'full-time') {
    showView('view-fulltime');
    renderFulltime();
  }
}

document.addEventListener('DOMContentLoaded', init);
