# Setup Guide

## Simple Setup - No Webhook Required!

This bot uses **polling** instead of webhooks, which means it works perfectly on localhost without any additional setup.

### How It Works

The application automatically polls Telegram's API every second to check for new messages. This means:
- ✅ No need for public URL
- ✅ No need for ngrok or tunneling services
- ✅ Works directly on localhost
- ✅ Just start the application and it works!

### Quick Start

1. **Set your bot token** in `src/main/resources/application.yml`:
   ```yaml
   telegram:
     bot-token: YOUR_BOT_TOKEN_HERE
     bot-username: YOUR_BOT_USERNAME
   ```

2. **Make sure your database is running** (PostgreSQL):
   ```bash
   docker compose up -d
   ```

3. **Start the application**:
   ```bash
   ./gradlew bootRun
   # or
   .\gradlew.bat bootRun
   ```

4. **Send `/start` to your bot** in Telegram - that's it!

The bot will:
- Automatically start polling for messages
- Register users when they send `/start`
- Save them to the database
- Respond with a welcome message

### Features

- **Automatic Polling**: Polls Telegram API every second for updates
- **User Registration**: Automatically saves users to database on `/start`
- **Train Monitoring**: Checks for available train seats every minute
- **Telegram Notifications**: Sends notifications when seats are available

### Configuration

All configuration is in `src/main/resources/application.yml`:

```yaml
telegram:
  bot-token: your-token-here
  bot-username: your-bot-username

railway:
  uz:
    base-url: https://e-ticket.railway.uz
    xsrf-token: your-xsrf-token
    cookie: your-cookie-optional

scheduler:
  check-interval-minutes: 1
```

### Troubleshooting

**Bot not responding?**
- Check that your bot token is correct in `application.yml`
- Check application logs for errors
- Make sure the application is running

**Database connection issues?**
- Ensure PostgreSQL is running: `docker compose up -d`
- Check database credentials in `application.yml`

**No updates received?**
- The bot automatically deletes any existing webhooks on startup
- Check logs for "Starting Telegram bot polling service"
- Make sure you're sending messages to the correct bot

That's it! Much simpler than webhooks, right? 🎉

