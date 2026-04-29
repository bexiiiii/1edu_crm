"""Stores admin Telegram chat IDs so they persist across restarts."""
import os
from config import CHAT_IDS_FILE, ADMIN_CHAT_IDS

_registered: set[int] = set(ADMIN_CHAT_IDS)


def _load():
    try:
        if os.path.exists(CHAT_IDS_FILE):
            with open(CHAT_IDS_FILE) as f:
                for line in f:
                    line = line.strip()
                    if line.isdigit():
                        _registered.add(int(line))
    except Exception:
        pass


def _save():
    try:
        os.makedirs(os.path.dirname(CHAT_IDS_FILE), exist_ok=True)
        with open(CHAT_IDS_FILE, "w") as f:
            for cid in _registered:
                f.write(f"{cid}\n")
    except Exception:
        pass


_load()


def register(chat_id: int):
    _registered.add(chat_id)
    _save()


def all_ids() -> list[int]:
    return list(_registered)


def is_registered(chat_id: int) -> bool:
    return chat_id in _registered
