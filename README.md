# Nana — Telegram AI Assistant

Nana is an AI-powered Telegram group assistant built with Java and Spring Boot.

The bot listens to group messages using Telegram Bot API long polling and generates AI-powered replies when users address Nana.

## Features

- Telegram Bot API integration using long polling
- AI-powered conversational replies
- Responds when users mention Nana, mention the bot username, or reply to the bot
- Group conversation context
- Duplicate message filtering
- Rate limiting
- Configurable AI model
- Spring Boot application

## Tech Stack

- Java
- Spring Boot
- Maven
- Telegram Bot API
- AI model integration

## Architecture

Nana receives Telegram updates through long polling.

When a message addresses the bot, Nana processes the message, prepares the conversation context, and sends a request to the configured AI model.

The generated response is then sent back to the Telegram group.

## Requirements

- Java 21
- Maven
- Telegram Bot Token
- Access to the configured AI provider

## Configuration

Configure the following environment variables:

| Variable | Description |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API token |
| `AI_API_KEY` | API key for the configured AI provider, if required |
| `AI_MODEL` | AI model name |

Do not commit real tokens, API keys, or other secrets to the repository.

## Running the Application

Clone the repository:

```bash
git clone https://github.com/YOUR_USERNAME/nana-telegram-ai-bot.git
cd nana-telegram-ai-bot
```

Configure the required environment variables.

Build the application:

```bash
mvn clean package
```

Run the application:

```bash
mvn spring-boot:run
```

## Project Status

This project is under active development.

## License

MIT
