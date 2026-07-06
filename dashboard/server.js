const express = require('express');
const net = require('net');
const path = require('path');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ─── RCON 설정 ─────────────────────────────────────────────────────────────
const RCON_HOST = '127.0.0.1';
const RCON_PORT = 25575;
const RCON_PASS = 'tapflag_dev';

// ─── RCON 패킷 ─────────────────────────────────────────────────────────────
const TYPE_LOGIN   = 3;
const TYPE_COMMAND = 2;
const TYPE_RESP    = 0;

function buildPacket(id, type, payload) {
  const body = Buffer.from(payload + '\0\0', 'utf8');
  const buf  = Buffer.allocUnsafe(4 + 4 + 4 + body.length);
  buf.writeInt32LE(8 + body.length, 0);   // length field
  buf.writeInt32LE(id,   4);
  buf.writeInt32LE(type, 8);
  body.copy(buf, 12);
  return buf;
}

function rcon(command) {
  return new Promise((resolve, reject) => {
    const sock = new net.Socket();
    let buf = Buffer.alloc(0);
    let authed = false;
    const timeout = setTimeout(() => { sock.destroy(); reject(new Error('RCON timeout')); }, 5000);

    sock.connect(RCON_PORT, RCON_HOST, () => {
      sock.write(buildPacket(1, TYPE_LOGIN, RCON_PASS));
    });

    sock.on('data', (data) => {
      buf = Buffer.concat([buf, data]);
      while (buf.length >= 4) {
        const len = buf.readInt32LE(0);
        if (buf.length < 4 + len) break;
        const id   = buf.readInt32LE(4);
        const type = buf.readInt32LE(8);
        const payload = buf.slice(12, 4 + len - 2).toString('utf8');
        buf = buf.slice(4 + len);

        if (!authed) {
          if (id === -1) { clearTimeout(timeout); sock.destroy(); reject(new Error('RCON auth failed')); return; }
          authed = true;
          sock.write(buildPacket(2, TYPE_COMMAND, command));
        } else {
          clearTimeout(timeout);
          sock.destroy();
          resolve(payload);
        }
      }
    });

    sock.on('error', (e) => { clearTimeout(timeout); reject(e); });
  });
}

// ─── RCON 결과에서 색코드 제거 ───────────────────────────────────────────────
function stripColor(str) {
  return str.replace(/§[0-9a-fklmnor]/gi, '');
}

// ─── API 라우트 ─────────────────────────────────────────────────────────────

// 게임 상태 (JSON)
app.get('/api/status', async (req, res) => {
  try {
    const raw = await rcon('tapflag apistatus');
    const clean = stripColor(raw);
    // RCON 응답에서 JSON 부분만 추출
    const jsonStart = clean.indexOf('{');
    if (jsonStart === -1) return res.json({ error: 'no data', raw: clean });
    const json = JSON.parse(clean.slice(jsonStart));
    res.json(json);
  } catch (e) {
    res.status(503).json({ error: e.message });
  }
});

// 서버 핑 (접속자 수 포함)
app.get('/api/ping', async (req, res) => {
  try {
    const raw = await rcon('list');
    res.json({ ok: true, message: stripColor(raw) });
  } catch (e) {
    res.status(503).json({ ok: false, error: e.message });
  }
});

// 타이머 점령 강제 on/off
app.post('/api/capture', async (req, res) => {
  const { on } = req.body;
  try {
    await rcon(`tapflag timer capture ${on ? 'on' : 'off'}`);
    res.json({ ok: true });
  } catch (e) {
    res.status(503).json({ ok: false, error: e.message });
  }
});

// 점령 시간 설정 (게임 타이머 직접 조작)
app.post('/api/timer/set', async (req, res) => {
  const { action } = req.body; // 'start' | 'stop'
  try {
    await rcon(`tapflag timer ${action}`);
    res.json({ ok: true });
  } catch (e) {
    res.status(503).json({ ok: false, error: e.message });
  }
});

// 게임 시작 (playtest)
app.post('/api/playtest/start', async (req, res) => {
  try {
    const players = await rcon('list');
    // 첫 번째 온라인 플레이어에게 playtest setup 실행 — 단순 구현
    res.json({ ok: false, message: '플레이테스트는 인게임 /tapflag playtest setup 사용' });
  } catch (e) {
    res.status(503).json({ ok: false, error: e.message });
  }
});

// 서버 종료
app.post('/api/shutdown', async (req, res) => {
  try {
    await rcon('stop');
    res.json({ ok: true });
  } catch (e) {
    // stop 명령 자체가 연결을 끊으므로 에러가 나도 성공으로 처리
    res.json({ ok: true });
  }
});

// ─── 서버 시작 ─────────────────────────────────────────────────────────────
const PORT = 3000;
app.listen(PORT, () => {
  console.log(`TapFlag Dashboard: http://localhost:${PORT}`);
});
