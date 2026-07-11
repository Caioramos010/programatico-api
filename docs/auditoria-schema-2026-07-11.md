# Auditoria de schema — 2026-07-11 (multi-agente)

Divergência entre grupos resolvida lendo o código: os grupos 1/4/5 afirmavam que deletes bloqueados por FK estouram **HTTP 500**; os grupos 2/3 diziam **409**. Confirmado em `GlobalExceptionHandler.java:31-36`: `DataIntegrityViolationException` é capturada e devolve **409 com a mensagem enganosa "Operação não permitida: registro duplicado."** — vale tanto para os deletes do admin quanto para a exclusão de conta. Também confirmei `UsuarioService.java:330-336`: a limpeza realmente só cobre 5 tabelas (e uma delas, `user_missions`, está vazia/morta). Segue o relatório.

---

# Relatório Executivo — Auditoria de Schema Programático

**Contexto:** 22 tabelas, sem Flyway/Liquibase, `ddl-auto=update` (nunca dropa nada). O mecanismo real de migração é o `SchemaMigrationRunner`. Todo drop precisa ser SQL manual ou statement idempotente no runner. **Atenção:** `SchemaMigrationRunner.java` e o workflow de deploy são *forbidden paths* pelas regras do repo — as ações abaixo que os tocam exigem sua aprovação explícita antes de executar.

## 1. Tabela-resumo (22 tabelas)

| # | Tabela | Veredito | Resumo |
|---|--------|----------|--------|
| 1 | `users` | **MANTER** | Central, todas as colunas vivas. Bug grave no fluxo de exclusão (ver ações). |
| 2 | `user_settings` | **MANTER** | Viva, mas com 4 colunas físicas mortas do TOTP (incl. segredos residuais) e 4 toggles placebo. |
| 3 | `verification_tokens` | **EXCLUIR** | Natimorta: entidade sem repository desde o commit inicial. 0 linhas. |
| 4 | `two_factor_backup_codes` | **EXCLUIR** | Órfã após remoção do TOTP (cc98634). 20 linhas de hashes criptograficamente inúteis. |
| 5 | `tracks` | **MANTER** | Nuclear. Linha de teste "Trilha de IA" (id=3) polui o seletor do Revisar. |
| 6 | `modules` | **MANTER** | Coração da progressão. Delete do admin quebra com módulo jogado. |
| 7 | `teoria_paginas` | **MANTER** | Viva. Sem reordenação; delete quebra com blocos filhos. |
| 8 | `content_blocks` | **CONSOLIDAR** | Viva, mas caminho "bloco direto no módulo" (pagina_id NULL) é armadilha morta; `module_id` redundante. |
| 9 | `exercises` | **MANTER** | Núcleo (449 linhas). Bug de formato de tags corrompe "top assuntos". |
| 10 | `skills` | **EXCLUIR** | Natimorta, sem repository, 0 linhas. Substituída por `exercises.tags`. |
| 11 | `exercise_skills` | **EXCLUIR** | Junção morta, 0 linhas, `@Id` nunca gerado. |
| 12 | `user_skill_performance` | **EXCLUIR** | Morta, 0 linhas. Métrica já é derivada on-the-fly das tags. |
| 13 | `practice_sessions` | **MANTER** | Central. Coluna `time_limit_seconds` sempre NULL (dropar); práticas abandonadas acumulam. |
| 14 | `practice_session_exercises` | **MANTER** | Onde vivem acurácia/maestria. Coluna `user_answer` write-only (dropar). |
| 15 | `user_progress` | **MANTER** | Essencial. Coluna `status` é constante (sempre COMPLETED); gating só visual. |
| 16 | `user_stats` | **MANTER** | XP/vidas/streak vivos. 3 bugs de streak/XP de missão. |
| 17 | `missions` | **MANTER** | Catálogo vivo. Delete do admin quebra; `objectiveType` sem validação; runner ressuscita missões. |
| 18 | `user_daily_missions` | **MANTER** | Tabela real de progresso de missões. Esquecida na exclusão de conta (bug nº 1). |
| 19 | `user_missions` | **EXCLUIR** | Fóssil pré-motor diário, 0 linhas. Sua existência mascarou o bug da exclusão de conta. |
| 20 | `payments` | **MANTER** | Viva e enxuta. Bug monetário no sync (ROOT infinito). |
| 21 | `processed_abacate_webhooks` | **MANTER** | Idempotência/anti-replay do webhook. 0 linhas = webhook nunca configurado, não código morto. |
| 22 | `notifications` | **MANTER** | Viva ponta a ponta. Preferências ignoradas pela maioria dos emissores; sem retenção. |

**Saldo:** 15 manter, 1 consolidar, 6 excluir. As 6 excluídas somam 20 linhas de dados (todas descartáveis).

## 2. Ações priorizadas

### P0 — Bugs que quebram produção hoje

| Ação | Esforço | Risco |
|------|---------|-------|
| **1. Corrigir exclusão de conta:** adicionar `deleteByUsuarioId` em `UserDailyMissionRepository`, `NotificationRepository`, `PaymentRepository`, `UserSettingsRepository` e chamar em `excluirRegistrosVinculados` (`UsuarioService.java:330-336`). Trocar a linha morta de `user_missions` pela de `user_daily_missions`. Hoje QUALQUER usuário ativo recebe 409 ao confirmar exclusão. | Médio | Baixo |
| **2. Bloquear reativação infinita de ROOT:** guard `existsByBillId` antes de `ativarPlanoRoot` nos dois caminhos de sync (`PaymentService.java:126-168`). Um checkout PAID antigo hoje rende +30 dias a cada `POST /api/payments/sync`. | Trivial | Baixo |
| **3. Fechar farm de XP em práticas:** em `responder()`, não creditar XP quando `sessao.getModulo() == null` (`SessaoAtividadeService.java:230, 591-596`). "Prática: Fixação" dá XP infinito. | Trivial | Baixo |
| **4. Unificar `parseTags` (JSON-first + fallback CSV)** em utilitário único e usar em `LearnService.java:361-369` e `OpenAiOrganizacaoService.java:80`. Top assuntos Root saem corrompidos para exercícios criados via admin. | Trivial | Baixo |

### P1 — Limpeza de schema (objetivo da auditoria)

| Ação | Esforço | Risco |
|------|---------|-------|
| **5. Deletar 6 entidades/artefatos mortos:** `VerificationToken.java`, `Skill.java`, `ExerciseSkill.java`, `UserSkillPerformance.java` + `UserSkillPerformanceId.java`, `UserMission.java` + `UserMissionRepository` + injeção/linha em `UsuarioService.java:38,333` + `UserMissionRepositoryTest` + mocks em `UsuarioServiceTest.java:65,503`. | Médio | Baixo |
| **6. DROP das 6 tabelas em prod/homolog** (ordem por FK): `user_skill_performance` → `exercise_skills` → `skills`; `verification_tokens`; `two_factor_backup_codes`; `user_missions`. Ideal: drops idempotentes no `SchemaMigrationRunner` (**forbidden path — pedir aprovação**); alternativa: SQL manual documentado em `db/migration/manual/`. `mysqldump` da `two_factor_backup_codes` antes se quiser trilha de auditoria. | Médio | Baixo |
| **7. Dropar colunas mortas:** `user_settings.two_factor_enabled/totp_enabled/totp_secret/totp_pending_secret` (segredos TOTP parados no banco — higiene de segurança), `practice_sessions.time_limit_seconds`, `practice_session_exercises.user_answer`. Remover `addColumnIfMissing` de `two_factor_enabled` em `SchemaMigrationRunner.java:186-187` (linha esquecida pelo cc98634). | Médio | Baixo |
| **8. Consolidar `content_blocks`:** remover rotas por módulo do `AdminContentBlockController` + funções órfãs no `adminService.ts`; `pagina_id` NOT NULL; trocar `countByModulo` por join via página; dropar `module_id`. Decidir destino do `LayoutType.CARDS` (implementar ou remover — hoje degrada para texto cru). | Médio | Médio |
| **9. Limpar lixo de teste em prod:** deletar "Trilha de IA" (id=3) bottom-up (1 exercício → 3 blocos → 1 página → 2 módulos → track). Zero user_progress, sem perda. | Trivial | Baixo |
| **10. Prevenir dupla trilha em rebuild:** remover `SEED_CONTENT_ENABLED=true` do `deploy-homolog.yml:71` (**forbidden path — pedir aprovação**), considerar deletar `SeedContentRunner.java`, e trocar `findFirstByOrderByDisplayOrderAsc` por variante com desempate por id (`LearnService.java:77,119`). | Trivial | Baixo |

### P2 — Regras de negócio e UX

| Ação | Esforço | Risco |
|------|---------|-------|
| **11. Gating server-side:** validar módulo desbloqueado em `getTeorico` e `iniciarSessao` (hoje o LOCKED é só UI — `LearnService.java:265-269`, `SessaoAtividadeService.java:76-104`). | Médio | Médio |
| **12. Deletes do admin honestos:** distinguir FK violation de duplicidade no `GlobalExceptionHandler.java:31-36` e implementar pré-checagem/cascata explícita em `AdminTrilhaService`, `AdminModuloService`, `AdminTeoriaPaginaService`, `AdminMissaoService`. Hoje todo delete bloqueado vira 409 "registro duplicado". | Médio | Baixo |
| **13. Centralizar preferências de notificação** dentro de `NotificationService.criarNotificacaoSistema` (mapear os dois enums `NotificationKind` divergentes) — ou remover os 4 toggles placebo da `SettingsPage.tsx`. | Médio | Baixo |
| **14. Três fixes em `user_stats`:** criar stats em `MissaoDiariaService.java:121` (XP de missão hoje é descartado em silêncio), contar teoria no streak, normalizar streak on-read em `getEstatisticas`. | Médio | Baixo |
| **15. `ReviewService.java:190-195` usar `activeSeconds`** (duração hoje infla ~259k s em sessão retomada dias depois) + encerrar sessões de prática abandonadas. | Trivial + Médio | Baixo |
| **16. Remover código morto:** `UsuarioService.excluir/listar` (:293-300, :234-239), overload `podeNotificar(Long,...)`, arquivos SQL de referência do 2FA em `db/migration/manual/`. | Trivial | Baixo |
| **17. Higiene menor:** validar `objectiveType` no CRUD de missões; alinhar `SeedContentRunner`/`MissaoCatalogoRunner`; corrigir upsert de payment que não atualiza `amount`; limitar/dropar `icon` LONGTEXT em `tracks`/`users`; retenção de `notifications`. | Médio | Baixo |

## 3. Top problemas de regra de negócio

1. **Exclusão de conta self-service quebrada** — `UsuarioService.java:330-336` não limpa `user_daily_missions` (84 linhas), `notifications` (43), `payments` (2) e `user_settings` (3); FKs NOT NULL sem cascade → 409 "registro duplicado" para praticamente todo usuário ativo. Agravante: limpa a tabela morta `user_missions`, o que mascarou o bug.
2. **Assinatura ROOT renovável infinitamente de graça** — `PaymentService.java:126-168`: o sync ativa +30 dias para qualquer checkout PAID histórico, sem checar consumo prévio. Um pagamento único = ROOT perpétuo.
3. **Farm de XP infinito** — `SessaoAtividadeService.java:591-596`: `moduloJaConcluido()` retorna sempre false para práticas (`modulo=null`), então toda resposta é "1ª tentativa" com XP integral, inflando também a missão EARN_XP.
4. **Bloqueio de módulos é só visual** — `LearnService.java:151-162` calcula LOCKED apenas para exibição; `getTeorico` (:265-269) e `iniciarSessao` não validam. Qualquer autenticado consome/conclui módulo do fim da trilha por GET/POST direto.
5. **"Top assuntos" Root corrompidos** — `LearnService.java:361-369` só faz split por vírgula, mas o admin grava tags como JSON (`AdminAtividadesPage.tsx:190`), produzindo assuntos como `["laços"`. Há 4 parseTags divergentes no código.
6. **4 dos 6 toggles de notificação são placebo** — `NotificationService.java:70-79` e emissores (`LearnService.java:329`, `SessaoAtividadeService.java:237,341`, `AbacatePayWebhookService.java:287`) ignoram preferências; os dois enums `NotificationKind` só coincidem em SUBSCRIPTION.
7. **"Deletar" usuário no admin não bloqueia ninguém** — `AdminUsuarioService.java:45-54` só seta `deletedAt`; JWT continua válido e o próximo login ressuscita a conta (`UsuarioService.java:94`). Bloqueio real seria `setAtivo(false)`.
8. **Deletes do admin falham com mensagem sem sentido** — `GlobalExceptionHandler.java:31-36` traduz toda FK violation como "registro duplicado"; módulo com progresso de aluno é indeletável sem nenhuma pista do porquê.
9. **XP de missão descartado silenciosamente** — `MissaoDiariaService.java:121` usa `ifPresent` em vez de criar `user_stats`; usuário só-teoria completa missão e não recebe o bônus.
10. **Rebuild de banco zerado entrega a trilha errada** — `deploy-homolog.yml:71` + dois seeders com `displayOrder=1` empatado tornam `findFirstByOrderByDisplayOrderAsc` não-determinístico; alunos cairiam na trilha de amostra de 6 módulos.

**Sequência sugerida de execução:** P0.1–P0.4 num PR de fixes (`fix/`), depois P1.5–P1.7 num PR de limpeza (`chore/schema-cleanup`) com os drops aplicados via SQL manual em homolog após validação — lembrando que itens 6 e 10 tocam forbidden paths (`SchemaMigrationRunner`, workflow de deploy) e dependem da sua aprovação explícita.