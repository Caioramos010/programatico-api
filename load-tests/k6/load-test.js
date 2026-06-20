// Teste de carga do Programatico (k6) — espelha o escopo do TCC de referência (Servi Já):
// perfis de 50/100/1000 VUs + um perfil progressivo até a saturação, com mix de
// navegação autenticada do aluno (80%) e leitura administrativa (20%).
//
// COMO RODAR (exemplos):
//   k6 run -e PERFIL=50   -e BASE_URL=http://localhost:8080 -e USER_TOKEN=... -e ADMIN_TOKEN=... load-test.js
//   k6 run -e PERFIL=100  ...
//   k6 run -e PERFIL=1000 ...
//   k6 run -e PERFIL=progressivo ...
//
// TOKENS: a API é autenticada e o login é em 2 etapas (código por e-mail), o que não dá
// para automatizar no k6. Gere os JWTs ANTES e passe por env:
//   - USER_TOKEN : JWT de um aluno de teste (seed) — usado nos endpoints /api/aprender/*
//   - ADMIN_TOKEN: JWT do admin — usado nos endpoints /api/admin/*
// (Sem token, os endpoints respondem 401 e o teste mede só a borda de autenticação.)

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const USER_TOKEN = __ENV.USER_TOKEN || "";
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || "";
const PERFIL = __ENV.PERFIL || "50";

// Perfis de carga (estágios: rampa de subida -> sustentação -> redução),
// inspirados nos cenários da Tabela 5 do Servi Já.
const PERFIS = {
  "50": [
    { duration: "30s", target: 50 },
    { duration: "1m", target: 50 },
    { duration: "15s", target: 0 },
  ],
  "100": [
    { duration: "1m", target: 100 },
    { duration: "2m", target: 100 },
    { duration: "30s", target: 0 },
  ],
  "1000": [
    { duration: "2m", target: 1000 },
    { duration: "3m", target: 1000 },
    { duration: "1m", target: 0 },
  ],
  // Aumenta a carga em degraus até identificar o ponto de saturação (find-limit).
  progressivo: [
    { duration: "1m", target: 100 },
    { duration: "1m", target: 300 },
    { duration: "1m", target: 600 },
    { duration: "1m", target: 1000 },
    { duration: "1m", target: 1500 },
    { duration: "1m", target: 2000 },
    { duration: "30s", target: 0 },
  ],
};

const erroNegocio = new Rate("erros_negocio");

export const options = {
  stages: PERFIS[PERFIL] || PERFIS["50"],
  thresholds: {
    // Metas de qualidade — ajustar conforme baseline observado no ambiente.
    http_req_duration: ["p(95)<800", "p(99)<2000"],
    http_req_failed: ["rate<0.05"],
    erros_negocio: ["rate<0.05"],
  },
};

function headersUser() {
  return USER_TOKEN ? { Authorization: `Bearer ${USER_TOKEN}` } : {};
}
function headersAdmin() {
  return ADMIN_TOKEN ? { Authorization: `Bearer ${ADMIN_TOKEN}` } : {};
}

// 80% navegação do aluno (leitura), 20% leitura administrativa — mesmo mix do Servi Já.
export default function () {
  if (Math.random() < 0.8) {
    navegacaoAluno();
  } else {
    leituraAdmin();
  }
  sleep(1 + Math.random() * 2); // think-time 1–3s
}

function navegacaoAluno() {
  const h = headersUser();
  const trilha = http.get(`${BASE}/api/aprender/trilha`, { headers: h, tags: { ep: "trilha" } });
  erroNegocio.add(trilha.status >= 500);
  check(trilha, { "trilha sem erro de servidor": (r) => r.status < 500 });

  http.get(`${BASE}/api/aprender/stats`, { headers: h, tags: { ep: "stats" } });
  http.get(`${BASE}/api/aprender/missoes`, { headers: h, tags: { ep: "missoes" } });
  http.get(`${BASE}/api/notificacoes`, { headers: h, tags: { ep: "notificacoes" } });
}

function leituraAdmin() {
  const h = headersAdmin();
  const dash = http.get(`${BASE}/api/admin/dashboard`, { headers: h, tags: { ep: "dashboard" } });
  erroNegocio.add(dash.status >= 500);
  check(dash, { "dashboard sem erro de servidor": (r) => r.status < 500 });

  http.get(`${BASE}/api/admin/trilhas`, { headers: h, tags: { ep: "admin-trilhas" } });
  http.get(`${BASE}/api/admin/usuarios`, { headers: h, tags: { ep: "admin-usuarios" } });
}
