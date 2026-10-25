# Nana

A small Java 21 / Spring Boot Telegram group assistant. It uses Telegram's official Bot API with long polling and the local Ollama HTTP API (`qwen2.5:7b`). It has no webhook, database, paid API, or persistent conversation store.

## Telegram setup

1. In a private chat with **@BotFather**, create/configure your bot and keep its token private.
2. For replies and explicit `@bot_username` mentions, Telegram privacy mode can stay enabled. For Nana to detect a plain message containing “Nana”, privacy mode must be **disabled** in BotFather (`/setprivacy` → select bot → Disable). Telegram requires removing and re-adding the bot to each group after changing this setting.
3. Add Nana to each target group. It does not need administrator permissions if privacy mode is disabled. If privacy stays enabled, admins still receive all group messages, but that grants broader access than needed.
4. Ensure the bot has no active webhook: Telegram does not allow `getUpdates` polling while a webhook is configured. Nana deliberately does not change webhook settings automatically.
5. Group data is held only in process memory, with a separate bounded, expiring window per group. It is discarded when the app stops. Logs omit message text and the token.

## Configure and run

The bot token is read only from `TELEGRAM_BOT_TOKEN`. In a terminal, enter it without echoing it, then start Nana:

```sh
cd /zahra/codes/nana
read -rsp 'Telegram bot token: ' TELEGRAM_BOT_TOKEN; echo
export TELEGRAM_BOT_TOKEN
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn spring-boot:run
```

Do not put the token in source control, command arguments, or logs. For an always-on deployment, provide `TELEGRAM_BOT_TOKEN` through a protected service environment file (mode `0600`) or a systemd credential. A user-service template is included at `deploy/nana.service.example`; copy it to `~/.config/systemd/user/nana.service` after placing the built jar at `~/apps/nana/nana.jar` and creating `~/.config/nana/nana.env`. Reload/enable it with `systemctl --user daemon-reload` and `systemctl --user enable --now nana.service`. To start user services at boot before login, systemd lingering must be enabled for your account; enabling that changes system configuration and is not done by this project.

Defaults are in `src/main/resources/application.properties`. Override them with environment variables, for example:

- `NANA_OLLAMA_BASE_URL` (default `http://localhost:11434`)
- `NANA_OLLAMA_MODEL` (default `qwen2.5:7b`)
- `NANA_CONTEXT_MAX_MESSAGES` (default `16`)
- `NANA_CONTEXT_MAX_AGE_MINUTES` (default `120`)
- `NANA_RATE_LIMIT_GROUP_SECONDS` (default `3`)
- `NANA_RESPONSE_MAX_CHARS` (default `1800`)

Spring Boot relaxed binding maps these environment variable names to the matching properties. Telegram's normal long-poll limit is configurable as `NANA_TELEGRAM_POLL_TIMEOUT_SECONDS`.

## Tests and package

```sh
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
mvn package
```

The included tests exercise addressing detection, per-group bounded/expiring context, duplicate update filtering, and rate limiting.
