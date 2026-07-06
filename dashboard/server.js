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

const TYPE_LOGIN   = 3;
const TYPE_COMMAND = 2;
const CMD_ID       = 10;
const END_ID       = 11;  // 끝 마커용 ID

function buildPacket(id, type, payload) {
  const body = Buffer.from(payload + '\x00\x00', 'utf8');
  const buf  = Buffer.allocUnsafe(4 + 4 + 4 + body.length);
  buf.writeInt32LE(8 + body.length, 0);
  buf.writeInt32LE(id,   4);
  buf.writeInt32LE(type, 8);
  body.copy(buf, 12);
  return buf;
}

// RCON: 인증 → 명령 실행 → 마커 명령으로 응답 끝 확인
function rcon(command) {
  return new Promise((resolve, reject) => {
    const sock = new net.Socket();
    let buf = Buffer.alloc(0);
    let authed = false;
    let parts = [];
    const timer = setTimeout(() => { sock.destroy(); reject(new Error('RCON timeout')); }, 6000);

    sock.connect(RCON_PORT, RCON_HOST, () => {
      sock.write(buildPacket(1, TYPE_LOGIN, RCON_PASS));
    });

    sock.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      while (buf.length >= 4) {
        const len = buf.readInt32LE(0);
        if (len < 10 || buf.length < 4 + len) break;
        const id      = buf.readInt32LE(4);
        const payload = buf.slice(12, 4 + len - 2).toString('utf8');
        buf = buf.slice(4 + len);

        if (!authed) {
          if (id === -1) { clearTimeout(timer); sock.destroy(); reject(new Error('RCON 인증 실패 (비밀번호 오류)')); return; }
          authed = true;
          sock.write(buildPacket(CMD_ID, TYPE_COMMAND, command));
          // 마커 명령: 응답이 도착하면 수집 완료
          sock.write(buildPacket(END_ID, TYPE_COMMAND, 'version'));
        } else if (id === END_ID) {
          clearTimeout(timer);
          sock.destroy();
          resolve(parts.join(''));
        } else if (id === CMD_ID && payload.length > 0) {
          parts.push(payload);
        }
        // id가 CMD_ID이고 payload가 비어있으면 무시 (일부 Minecraft 서버가 빈 패킷 선송신)
      }
    });

    sock.on('error', (e) => { clearTimeout(timer); reject(e); });
    sock.on('close', () => {
      if (!authed) { clearTimeout(timer); reject(new Error('RCON 연결 끊김 (서버 꺼짐?)')); }
    });
  });
}

function stripColor(str) {
  return str.replace(/§[0-9a-fklmnor]/gi, '').replace(/§[0-9a-fklmnor]/gi, '');
}

// ─── API ───────────────────────────────────────────────────────────────────

app.get('/api/status', async (req, res) => {
  try {
    const raw   = await rcon('tapflag apistatus');
    const clean = stripColor(raw);
    const start = clean.indexOf('{');
    const end   = clean.lastIndexOf('}');
    if (start === -1 || end === -1) {
      return res.status(503).json({ error: 'JSON 없음', raw: clean.slice(0, 200) });
    }
    const json = JSON.parse(clean.slice(start, end + 1));
    res.json(json);
  } catch (e) {
    res.status(503).json({ error: e.message });
  }
});

app.post('/api/capture', async (req, res) => {
  const { on } = req.body;
  try {
    await rcon(`tapflag timer capture ${on ? 'on' : 'off'}`);
    res.json({ ok: true });
  } catch (e) {
    res.status(503).json({ ok: false, error: e.message });
  }
});

app.post('/api/timer/set', async (req, res) => {
  const { action } = req.body;
  if (!['start', 'stop'].includes(action)) return res.status(400).json({ ok: false });
  try {
    await rcon(`tapflag timer ${action}`);
    res.json({ ok: true });
  } catch (e) {
    res.status(503).json({ ok: false, error: e.message });
  }
});

app.post('/api/shutdown', async (req, res) => {
  try {
    await rcon('stop');
    res.json({ ok: true });
  } catch (e) {
    res.json({ ok: true }); // stop은 연결 끊기므로 에러여도 성공
  }
});

// ─── 서버 시작 ──────────────────────────────────────────────────────────────
const PORT = 3000;
app.listen(PORT, () => {
  console.log(`TapFlag Dashboard: http://localhost:${PORT}`);
  console.log(`RCON: ${RCON_HOST}:${RCON_PORT}`);
});
