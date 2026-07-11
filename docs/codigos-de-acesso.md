# Códigos de acesso — processo e decisões técnicas

Documentação do ciclo de vida dos códigos enviados por e-mail (ativação de conta,
verificação de login, redefinição de senha e exclusão de conta), em resposta ao
feedback da banca: *"revisar a lógica de geração do código de acesso, garantindo
previsibilidade e ajustar a documentação deste processo"*.

## 1. Geração (`CodigoAcesso.gerar`)

- **Fonte de aleatoriedade**: instância única de `java.security.SecureRandom`
  (CSPRNG do JDK, thread-safe). Uma única instância evita o custo de re-semear a
  cada código e segue a recomendação da revisão interna do grupo.
- **Formato**: 6 caracteres sobre um alfabeto alfanumérico de 32 símbolos
  (`A–Z` e `2–9`, excluindo os ambíguos `I`, `O`, `0` e `1` para facilitar a
  digitação a partir do e-mail).
- **Espaço de busca**: 32⁶ ≈ **1,07 bilhão** de combinações — cerca de 1000×
  o espaço de um código de 6 dígitos numéricos (10⁶).

## 2. Armazenamento (`CodigoAcesso.hash`)

- O banco **nunca guarda o código em claro**: persiste-se apenas o **SHA-256**
  (hex, 64 caracteres) do código normalizado (`trim` + maiúsculas — o usuário
  pode digitar em minúsculas).
- O hash é determinístico para permitir a localização do usuário pelo código
  (fluxos sem e-mail: `findByCodigoAtivacao`, `findByCodigoRedefinicaoSenha`).
- A proteção contra força bruta **não** depende só do hash: combina o espaço de
  32⁶, a **expiração** e o **limite de tentativas** (`VerificationCodeGuardService`,
  com bloqueio temporário por contexto: ativação, login e redefinição).

## 3. Validade e invalidação

| Código | Validade | Observações |
|---|---|---|
| Ativação de conta | **24 h** | Reenvio (`/ativar/solicitar`) gera código novo e invalida o anterior (sobrescrita). |
| Verificação de login | **1 h** | Reenvio (`/login/reenviar`) reaproveita o fluxo de iniciar login (código novo). |
| Redefinição de senha | **24 h** | Código consumido (zerado) ao concluir a troca. |
| Exclusão de conta | **24 h** | Código consumido ao confirmar a exclusão. |

- Todos os códigos são **de uso único**: ao serem aceitos, o campo e a expiração
  são zerados na mesma transação.
- Códigos emitidos **antes** da política de expiração da ativação (campo nulo)
  seguem válidos por compatibilidade; qualquer reenvio já os coloca na regra nova.

## 4. Fluxo ponta a ponta (ativação como exemplo)

1. `POST /api/auth/registro` → gera código (claro) → grava `sha256(código)` +
   expiração (24 h) → e-mail com o código em claro.
2. Usuário digita o código → `POST /api/auth/ativar`.
3. Backend: localiza por `sha256(código digitado)` (ou por e-mail + comparação
   de hash), valida expiração e o guard de tentativas.
4. Sucesso → conta ativa, código zerado e **sessão já logada** (o código provou
   posse do e-mail; não se exige um segundo código no primeiro login).

## 5. O que mudou nesta revisão (2026-07-11)

- `SecureRandom` passou a instância única (antes: uma nova por chamada).
- Códigos passaram de 6 dígitos (10⁶) para 6 alfanuméricos sem ambíguos (32⁶).
- Banco passou a armazenar SHA-256 em vez de texto puro (colunas ampliadas
  para 64 chars).
- Código de ativação ganhou expiração de 24 h (antes não expirava).
- **Efeito colateral controlado**: códigos pendentes emitidos antes do deploy
  (texto puro no banco) deixam de validar — o usuário resolve com "reenviar código".
