# Evelyn Project

Projeto que reúne o **backend** (API FastAPI, estado, emoção, LLM, TTS, push) e o **app Android** (Compose) para a personagem Evelyn.

## Estrutura

- **`backend/`** – API Python (FastAPI), estado em JSON, memória, emoção, personality modes, nonverbal policy, voice profiles, STT/TTS (OpenAI/Inworld), FCM.
- **`android/`** – App Android (Kotlin + Compose): chat, gravação de voz, pseudo-sync (ForegroundService + VAD), inclusão de vozes, modos de personalidade.

## Backend

- **Requisitos:** Python 3.10+, ver `backend/requirements.txt`.
- **Variáveis de ambiente (principais):** `OPENAI_API_KEY`, `JWT_SECRET` (ou `AUTH_DISABLED=1` em dev), `DATA_DIR`, `PUBLIC_BASE_URL`. Opcionais: `INWORLD_API_KEY` (TTS), `DATABASE_URL`, FCM.
- **Rodar:** a partir de `backend/`: `uvicorn server:app --reload` (ou usar `render.yaml` para deploy).
- **Testes:** `cd backend && pytest` (com `AUTH_DISABLED=1` os testes usam `user_id` no path).

## Android

- **Configuração:** em `android/app/build.gradle.kts` estão `BASE_URL` e `DEFAULT_USER_ID` (BuildConfig). Para outro servidor/usuário, altere lá ou use build flavors.
- **Requisitos:** Android SDK 26+, Kotlin 1.9, Compose. Firebase (google-services) para FCM.
- **Permissões:** microfone, notificações, (opcional) foreground service para pseudo-sync.

## Melhorias sugeridas

- **Backend:** não versionar `backend/data/` (estado e mídia) no Git; usar `.env` para segredos.
- **Android:** em produção, usar login real e JWT; `userId` e `baseUrl` já vêm do BuildConfig para facilitar builds diferentes.
- **Segurança:** com auth ativa, todas as rotas por usuário validam JWT vs `user_id` no path.

## Configuração do Firebase

O arquivo `google-services.json` não está no repositório por motivos de segurança.

Para rodar o projeto:

1. Vá no Firebase Console
2. Baixe o arquivo `google-services.json`
3. Coloque em:
   android/app/google-services.json