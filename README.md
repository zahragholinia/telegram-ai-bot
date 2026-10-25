# Nana

A Java 21 / Spring Boot Telegram group assistant. It uses Telegram's official Bot API with long polling and Google's Gemini `generateContent` API. Conversation snippets and participant identities are kept in memory separately for each group and expire after the configured age. No webhook or persistent conversation store is used.

## Configure and run

Nana requires `TELEGRAM_BOT_TOKEN` and `GEMINI_API_KEY` in the process environment. Both values are required and must be kept private. The Telegram and Gemini HTTP clients retain the existing proxy at `127.0.0.1:8081`; start that proxy before Nana.

```sh
cd /home/zahra/codes/nana
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
read -rsp 'Telegram bot token: ' TELEGRAM_BOT_TOKEN; echo
export TELEGRAM_BOT_TOKEN
read -rsp 'Gemini API key: ' GEMINI_API_KEY; echo
export GEMINI_API_KEY
mvn spring-boot:run
```

The bot must be added to a group. For unmentioned contextual participation, disable Telegram privacy mode for the bot in BotFather and then remove and re-add the bot to groups. Nana ignores its own messages. It responds to direct mentions and replies to Nana, and selectively responds to group questions when recent context suggests a continuing discussion. Messages clearly addressed to another participant are left to that person. Unrelated messages do not trigger a Gemini request.

Defaults are in `src/main/resources/application.properties`. Context defaults to 16 messages and 120 minutes; group response cooldown defaults to 3 seconds. Override these with Spring environment properties such as `NANA_CONTEXT_MAX_MESSAGES`, `NANA_CONTEXT_MAX_AGE_MINUTES`, `NANA_RATE_LIMIT_GROUP_SECONDS`, and `NANA_RESPONSE_MAX_CHARS`.

## Tests and package

```sh
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn test
mvn package
```
