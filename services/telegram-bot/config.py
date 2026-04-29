import os

BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")

# Базовый URL API (через nginx / api-gateway или напрямую)
API_BASE = os.getenv("API_BASE_URL", "http://api-gateway:8090")

# Keycloak для получения SUPER_ADMIN токена
KC_URL = os.getenv("KEYCLOAK_INTERNAL_URL", "http://keycloak:8080/auth")
KC_REALM = os.getenv("KEYCLOAK_REALM", "ondeedu")
KC_CLIENT_ID = os.getenv("KEYCLOAK_CLIENT_ID", "crm-admin")
KC_CLIENT_SECRET = os.getenv("KEYCLOAK_CLIENT_SECRET", "")
SUPER_ADMIN_USERNAME = os.getenv("SUPER_ADMIN_USERNAME", "")
SUPER_ADMIN_PASSWORD = os.getenv("SUPER_ADMIN_PASSWORD", "")

# Telegram chat IDs администраторов (через запятую)
ADMIN_CHAT_IDS_RAW = os.getenv("TELEGRAM_ADMIN_CHAT_IDS", "")
ADMIN_CHAT_IDS: set[int] = set()
if ADMIN_CHAT_IDS_RAW:
    for cid in ADMIN_CHAT_IDS_RAW.split(","):
        try:
            ADMIN_CHAT_IDS.add(int(cid.strip()))
        except ValueError:
            pass

# Путь к файлу с chat IDs (добавляются через /start)
CHAT_IDS_FILE = os.getenv("CHAT_IDS_FILE", "/data/admin_chat_ids.txt")
