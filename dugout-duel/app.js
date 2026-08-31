(() => {
  const cfg = window.DUGOUT_CONFIG;
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const storageKey = 'dugout-duel-session-v1';
  let formMode = 'host';
  let selectedArch = 'balanced';
  let session = null;
  let room = null;
  let pollTimer = null;
  let busy = false;

  const archetypes = {
    balanced: { contact: 56, power: 56, discipline: 56, speed: 56, defense: 56 },
    contact: { contact: 68, power: 44, discipline: 60, speed: 57, defense: 63 },
    slugger: { contact: 49, power: 72, discipline: 53, speed: 43, defense: 49 },
    speed: { contact: 61, power: 44, discipline: 54, speed: 74, defense: 62 }
  };

  function esc(v = '') {
    return String(v).replace(/[&<>'"]/g, (c) => ({ '&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;' }[c]));
  }

  function toast(msg) {
    const el = $('#toast');
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.remove('show'), 1800);
  }

  function show(id) {
    ['#homeView','#createView','#gameView'].forEach((v) => $(v).classList.add('hidden'));
    $(id).classList.remove('hidden');
  }

  async function rpc(name, body) {
    const res = await fetch(`${cfg.supabaseUrl}/rest/v1/rpc/${name}`, {
      method: 'POST',
      headers: {
        apikey: cfg.publishableKey,
        Authorization: `Bearer ${cfg.publishableKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    });
    const text = await res.text();
    let data;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    if (!res.ok) {
      const raw = data?.message || data?.hint || String(data || res.statusText);
      throw new Error(raw);
    }
    return data;
  }

  function saveSession(next) {
    session = next;
    localStorage.setItem(storageKey, JSON.stringify(next));
    $('#resumeBtn').classList.remove('hidden');
  }

  function clearSession() {
    localStorage.removeItem(storageKey);
    session = null;
    room = null;
    clearInterval(pollTimer);
    pollTimer = null;
    $('#resumeBtn').classList.add('hidden');
  }

  function readSession() {
    try { return JSON.parse(localStorage.getItem(storageKey) || 'null'); }
    catch { return null; }
  }

  function playerFromForm() {
    const base = archetypes[selectedArch];
    return {
      name: $('#playerName').value.trim(),
      number: Number($('#playerNumber').value || 7),
      position: $('#playerPosition').value,
      archetype: selectedArch,
      wishTeam: $('#wishTeam').value,
      ...base
    };
  }

  function openForm(mode) {
    formMode = mode;
    show('#createView');
    const joining = mode === 'guest';
    $('#joinCodeWrap').classList.toggle('hidden', !joining);
    $('#formEyebrow').textContent = joining ? 'JOIN PRIVATE LEAGUE' : 'PLAYER CREATION';
    $('#formTitle').textContent = joining ? '친구 방에 들어간다.' : '네 선수부터 만들자.';
    $('#startBtn').textContent = joining ? '방 참가하기' : '방 만들기';
    if (joining) $('#joinCode').focus(); else $('#playerName').focus();
  }

  function avg(stats) {
    const pa = Number(stats?.pa || 0);
    const h = Number(stats?.h || 0);
    return pa ? (h / pa).toFixed(3).replace(/^0/, '') : '.000';
  }

  function playerCard(player, slot) {
    if (!player) return '';
    const st = player.seasonStats || {};
    const mine = session?.slot === slot;
    const stage = player.stage === 'HIGH_SCHOOL'
      ? '고3 · 드래프트 쇼케이스'
      : `${esc(player.team)} · PRO ${Number(player.proYear || 1)}년차`;
    const draft = player.draft ? ` · ${esc(player.draft)}` : '';
    const attrs = [
      ['CON', player.contact], ['POW', player.power], ['DISC', player.discipline],
      ['SPD', player.speed], ['DEF', player.defense], ['COND', player.condition]
    ];
    return `<article class="player-card ${mine ? 'me' : ''}">
      <div class="tag">${mine ? 'YOU' : 'RIVAL'} · #${Number(player.number || 0)} ${esc(player.position)}</div>
      <h3>${esc(player.name)}</h3>
      <div class="sub">${stage}${draft}</div>
      <div class="big-stat"><div class="avg">${avg(st)}</div><div class="war">WAR <b>${Number(st.war || 0).toFixed(2)}</b><br>주간 맞대결 ${Number(player.duelWins || 0)}승</div></div>
      <div class="stat-row">
        <div class="mini-stat"><span>HR</span><b>${Number(st.hr || 0)}</b></div>
        <div class="mini-stat"><span>RBI</span><b>${Number(st.rbi || 0)}</b></div>
        <div class="mini-stat"><span>H</span><b>${Number(st.h || 0)}</b></div>
        <div class="mini-stat"><span>G</span><b>${Number(st.g || 0)}</b></div>
      </div>
      <div class="bars">${attrs.map(([k,v]) => `<div class="bar"><span>${k}</span><div class="track"><div class="fill" style="width:${Math.max(0,Math.min(100,Number(v||0)))}%"></div></div><b>${Number(v||0)}</b></div>`).join('')}</div>
    </article>`;
  }

  function renderRoom() {
    if (!room || !session) return;
    show('#gameView');
    $('#roomCodeBtn').textContent = session.code;
    $('#seasonLabel').textContent = room.season === 1 ? 'HIGH SCHOOL' : `CAREER SEASON ${room.season}`;
    $('#weekLabel').textContent = `WEEK ${room.week} / 12`;

    const waiting = room.phase === 'waiting' || !room.guest;
    $('#waitingPanel').classList.toggle('hidden', !waiting);
    $('#matchPanel').classList.toggle('hidden', waiting);
    $('#inviteCode').textContent = session.code;
    if (waiting) return;

    $('#versusCards').innerHTML = `${playerCard(room.host,'host')}<div class="vs-mark">VS</div>${playerCard(room.guest,'guest')}`;

    const myReady = session.slot === 'host' ? room.hostReady : room.guestReady;
    const rivalReady = session.slot === 'host' ? room.guestReady : room.hostReady;
    $('#readyState').innerHTML = `나 ${myReady ? '✓ 제출완료' : '● 선택 대기'}<br>상대 ${rivalReady ? '✓ 제출완료' : '● 선택 대기'}`;
    $$('#actionGrid button').forEach((b) => b.disabled = Boolean(myReady || busy));

    const feed = Array.isArray(room.feed) ? room.feed.slice(-10).reverse() : [];
    $('#feed').innerHTML = feed.length
      ? feed.map((f) => `<div class="feed-line"><span>${esc(f.msg || '')}</span><small>${formatTime(f.t)}</small></div>`).join('')
      : '<div class="feed-line"><span>아직 기록 없음</span></div>';
  }

  function formatTime(epoch) {
    if (!epoch) return '';
    const d = new Date(Number(epoch) * 1000);
    return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  }

  async function refreshRoom(silent = true) {
    if (!session || busy) return;
    try {
      const next = await rpc('dugout_get_room', { p_code: session.code, p_token: session.token });
      const oldWeek = room?.week;
      const oldSeason = room?.season;
      room = next;
      renderRoom();
      if (!silent && (oldWeek !== next.week || oldSeason !== next.season)) toast('새 결과 반영됨');
    } catch (e) {
      if (!silent) toast(normalizeError(e.message));
    }
  }

  function startPolling() {
    clearInterval(pollTimer);
    pollTimer = setInterval(() => refreshRoom(false), 1800);
  }

  function normalizeError(msg = '') {
    if (msg.includes('ROOM_NOT_FOUND')) return '방 코드를 확인해라';
    if (msg.includes('ROOM_FULL')) return '이미 2명 꽉 찬 방임';
    if (msg.includes('ROOM_DENIED')) return '이 기기의 참가 토큰이 유효하지 않음';
    if (msg.includes('ALREADY_SUBMITTED')) return '이번 주 선택은 이미 제출함';
    if (msg.includes('ROOM_NOT_PLAYING')) return '아직 친구가 안 들어왔음';
    return '처리 실패: ' + msg.slice(0, 80);
  }

  async function createOrJoin(e) {
    e.preventDefault();
    if (busy) return;
    const player = playerFromForm();
    if (!player.name) return toast('선수 이름부터 써라');
    busy = true;
    $('#startBtn').disabled = true;
    try {
      if (formMode === 'host') {
        const data = await rpc('dugout_create_room', { p_player: player });
        saveSession({ code: data.code, token: data.token, slot: 'host' });
        room = data.room;
      } else {
        const code = $('#joinCode').value.trim().toUpperCase();
        if (code.length < 8) throw new Error('ROOM_NOT_FOUND');
        const data = await rpc('dugout_join_room', { p_code: code, p_player: player });
        saveSession({ code: data.code, token: data.token, slot: 'guest' });
        room = data.room;
      }
      renderRoom();
      startPolling();
      history.replaceState({}, '', `${location.pathname}?room=${session.code}`);
    } catch (e2) {
      toast(normalizeError(e2.message));
    } finally {
      busy = false;
      $('#startBtn').disabled = false;
    }
  }

  async function submitAction(action) {
    if (!session || busy) return;
    busy = true;
    renderRoom();
    try {
      room = await rpc('dugout_submit_action', { p_code: session.code, p_token: session.token, p_action: action });
      renderRoom();
      const meReady = session.slot === 'host' ? room.hostReady : room.guestReady;
      toast(meReady ? '제출 완료 · 상대 기다리는 중' : '둘 다 제출 · 이번 주 결과 떴다');
    } catch (e) {
      toast(normalizeError(e.message));
    } finally {
      busy = false;
      renderRoom();
    }
  }

  async function resume() {
    const saved = readSession();
    if (!saved?.code || !saved?.token || !saved?.slot) return;
    session = saved;
    try {
      room = await rpc('dugout_get_room', { p_code: saved.code, p_token: saved.token });
      renderRoom();
      startPolling();
    } catch (e) {
      clearSession();
      show('#homeView');
      toast('저장된 방을 찾지 못했음');
    }
  }

  async function copyInvite() {
    const url = `${location.origin}${location.pathname}?room=${session.code}`;
    try { await navigator.clipboard.writeText(url); toast('초대 링크 복사됨'); }
    catch { prompt('이 링크 복사', url); }
  }

  function bind() {
    $('#createModeBtn').addEventListener('click', () => openForm('host'));
    $('#joinModeBtn').addEventListener('click', () => openForm('guest'));
    $('#resumeBtn').addEventListener('click', resume);
    $$('[data-back]').forEach((b) => b.addEventListener('click', () => show('#homeView')));
    $$('#archetypeChoices .choice').forEach((b) => b.addEventListener('click', () => {
      selectedArch = b.dataset.arch;
      $$('#archetypeChoices .choice').forEach((x) => x.classList.toggle('selected', x === b));
    }));
    $('#playerForm').addEventListener('submit', createOrJoin);
    $$('#actionGrid button').forEach((b) => b.addEventListener('click', () => submitAction(b.dataset.action)));
    $('#shareBtn').addEventListener('click', copyInvite);
    $('#inviteCode').addEventListener('click', copyInvite);
    $('#roomCodeBtn').addEventListener('click', async () => {
      try { await navigator.clipboard.writeText(session.code); toast('방 코드 복사됨'); } catch {}
    });
    $('#leaveBtn').addEventListener('click', () => {
      clearSession();
      history.replaceState({}, '', location.pathname);
      show('#homeView');
      toast('이 기기 저장만 지웠음');
    });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshRoom(true); });
  }

  function boot() {
    bind();
    const saved = readSession();
    if (saved?.code) $('#resumeBtn').classList.remove('hidden');
    const invited = new URLSearchParams(location.search).get('room');
    if (invited && !saved) {
      openForm('guest');
      $('#joinCode').value = invited.toUpperCase().slice(0, 10);
    } else if (saved) {
      resume();
    }
  }

  boot();
})();
