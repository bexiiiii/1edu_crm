import asyncio
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, HTTPException
from telegram import (
    Bot, Update, InlineKeyboardButton, InlineKeyboardMarkup,
)
from telegram.ext import (
    Application, CommandHandler, CallbackQueryHandler, MessageHandler,
    ContextTypes, filters,
)

import api_client as api
import chat_store as store
from config import BOT_TOKEN, ADMIN_CHAT_IDS

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

PLAN_LABELS = {
    "BASIC": "Basic (20 000₸/мес)",
    "EXTENDED": "Extended (30 000₸/мес)",
    "EXTENDED_PLUS": "Extended+ (50 000₸/мес)",
}

# ─── Telegram handlers ────────────────────────────────────────────────────────

async def cmd_start(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    cid = update.effective_chat.id
    if ADMIN_CHAT_IDS and cid not in ADMIN_CHAT_IDS:
        await update.message.reply_text("⛔ Доступ запрещён для этого chat_id.")
        return
    store.register(cid)
    await update.message.reply_text(
        "✅ *1edu CRM — SUPER ADMIN бот*\n\n"
        "Ваш chat\\_id зарегистрирован. Вы будете получать уведомления.\n\n"
        "Доступные команды:\n"
        "/stats — Платформенная аналитика\n"
        "/tenants — Список последних УЦ\n"
        "/uc <id> — Детали конкретного УЦ\n"
        "/help — Справка",
        parse_mode="Markdown",
    )


async def cmd_stats(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    cid = update.effective_chat.id
    if not store.is_registered(cid):
        await update.message.reply_text("⛔ Нет доступа. Отправьте /start")
        return

    await update.message.reply_text("⏳ Загружаю аналитику...")
    try:
        kpis = await api.get_platform_kpis()
        trend = await api.get_revenue_trend(3)

        text = (
            "📊 *Платформенная аналитика*\n\n"
            f"🏢 Всего УЦ: *{kpis.get('totalTenants', '?')}*\n"
            f"🟢 Активных УЦ: *{kpis.get('activeTenants', '?')}*\n"
            f"🧪 На пробном: *{kpis.get('trialTenants', '?')}*\n"
            f"👥 Всего студентов: *{kpis.get('totalStudents', '?')}*\n"
            f"👨‍💼 Всего сотрудников: *{kpis.get('totalStaff', '?')}*\n\n"
            f"💰 MRR: *{_fmt_money(kpis.get('platformMrrEstimate'))}*\n"
            f"💰 ARR: *{_fmt_money(kpis.get('platformArrEstimate'))}*\n"
            f"📈 ARPU: *{_fmt_money(kpis.get('arpu'))}*\n\n"
            f"🔵 Basic: *{kpis.get('basicCount', 0)}* | "
            f"🟢 Extended: *{kpis.get('extendedCount', 0)}* | "
            f"⭐ Extended+: *{kpis.get('extendedPlusCount', 0)}*"
        )

        if trend:
            text += "\n\n📅 *Выручка (последние 3 мес):*\n"
            for t in trend[-3:]:
                text += f"  {t.get('month', '')}: *{_fmt_money(t.get('totalRevenue'))}*\n"

        await update.message.reply_text(text, parse_mode="Markdown")
    except Exception as e:
        logger.error("stats error: %s", e)
        await update.message.reply_text(f"❌ Ошибка: {e}")


async def cmd_tenants(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    cid = update.effective_chat.id
    if not store.is_registered(cid):
        await update.message.reply_text("⛔ Нет доступа.")
        return

    await update.message.reply_text("⏳ Загружаю список УЦ...")
    try:
        items = await api.get_tenants()
        items = sorted(items, key=lambda t: t.get("createdAt") or "", reverse=True)[:10]
        if not items:
            await update.message.reply_text("УЦ не найдены.")
            return

        text = "🏢 *Последние образовательные центры:*\n\n"
        for t in items:
            status_icon = _status_icon(t.get("status", ""))
            text += f"{status_icon} *{t.get('name', '?')}*\n"
            text += f"   ID: `{t.get('id', '?')}`\n"
            text += f"   Тариф: {t.get('plan', '?')} | Статус: {t.get('status', '?')}\n\n"

        keyboard = [[InlineKeyboardButton("🔄 Обновить", callback_data="refresh_tenants")]]
        await update.message.reply_text(
            text,
            parse_mode="Markdown",
            reply_markup=InlineKeyboardMarkup(keyboard),
        )
    except Exception as e:
        logger.error("tenants error: %s", e)
        await update.message.reply_text(f"❌ Ошибка: {e}")


async def cmd_uc(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    cid = update.effective_chat.id
    if not store.is_registered(cid):
        await update.message.reply_text("⛔ Нет доступа.")
        return

    if not ctx.args:
        await update.message.reply_text("Использование: /uc <tenant_id>")
        return

    tenant_id = ctx.args[0]
    await _send_tenant_details(update.message.reply_text, tenant_id)


async def _send_tenant_details(reply_fn, tenant_id: str):
    try:
        d = await api.get_tenant(tenant_id)
        tenant = d.get("tenantInfo", {})
        name = tenant.get("name", "?")
        status = tenant.get("status", "?")
        plan = tenant.get("plan", "?")

        text = (
            f"🏢 *{name}*\n"
            f"ID: `{tenant_id}`\n"
            f"Статус: {_status_icon(status)} {status} | Тариф: {plan}\n\n"
            f"👥 Студентов: {d.get('totalStudents', '?')} (активных: {d.get('activeStudents', '?')})\n"
            f"👨‍💼 Сотрудников: {d.get('totalStaff', '?')}\n"
            f"📚 Активных подписок: {d.get('activeSubscriptions', '?')}\n"
            f"💰 Выручка (месяц): {_fmt_money(d.get('revenueThisMonth'))}\n"
            f"💰 Выручка (всего): {_fmt_money(d.get('revenueTotal'))}\n"
            f"💸 Расходы (месяц): {_fmt_money(d.get('expensesThisMonth'))}\n"
            f"📈 Прибыль (месяц): {_fmt_money(d.get('profitThisMonth'))}\n"
        )

        btns = []
        if status == "BANNED":
            btns.append(InlineKeyboardButton("✅ Разбанить", callback_data=f"unban:{tenant_id}"))
        else:
            btns.append(InlineKeyboardButton("🚫 Забанить", callback_data=f"ban:{tenant_id}"))

        plan_btns = []
        for p in ["BASIC", "EXTENDED", "EXTENDED_PLUS"]:
            if p != plan:
                plan_btns.append(InlineKeyboardButton(f"📋 → {p}", callback_data=f"plan:{tenant_id}:{p}"))

        keyboard = [btns, plan_btns]
        await reply_fn(text, parse_mode="Markdown", reply_markup=InlineKeyboardMarkup(keyboard))
    except Exception as e:
        await reply_fn(f"❌ Ошибка: {e}")


async def on_callback(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    data = query.data

    if data == "refresh_tenants":
        await cmd_tenants_via_callback(query)
        return

    if data.startswith("info:"):
        tenant_id = data.split(":", 1)[1]
        await query.edit_message_text("⏳ Загружаю детали УЦ...")
        await _send_tenant_details(query.message.reply_text, tenant_id)
        return

    if data.startswith("ban:"):
        tenant_id = data.split(":", 1)[1]
        ok = await api.ban_tenant(tenant_id, "Заблокировано администратором через Telegram")
        msg = "✅ УЦ заблокирован." if ok else "❌ Ошибка при блокировке."
        await query.edit_message_text(msg)

    elif data.startswith("unban:"):
        tenant_id = data.split(":", 1)[1]
        ok = await api.unban_tenant(tenant_id)
        msg = "✅ УЦ разблокирован." if ok else "❌ Ошибка при разблокировке."
        await query.edit_message_text(msg)

    elif data.startswith("plan:"):
        parts = data.split(":")
        tenant_id, plan = parts[1], parts[2]
        ok = await api.change_plan(tenant_id, plan)
        msg = f"✅ Тариф изменён на {plan}." if ok else "❌ Ошибка при смене тарифа."
        await query.edit_message_text(msg)

    elif data.startswith("activate:"):
        parts = data.split(":")
        tenant_id, plan, billing = parts[1], parts[2], parts[3]
        ok = await api.activate_subscription(tenant_id, plan, billing)
        msg = f"✅ Подписка активирована ({plan}, {billing})." if ok else "❌ Ошибка."
        await query.edit_message_text(msg)


async def cmd_tenants_via_callback(query):
    try:
        items = await api.get_tenants()
        items = sorted(items, key=lambda t: t.get("createdAt") or "", reverse=True)[:10]
        text = "🏢 *Последние УЦ (обновлено):*\n\n"
        for t in items:
            status_icon = _status_icon(t.get("status", ""))
            text += f"{status_icon} *{t.get('name', '?')}* — `{t.get('id', '')[:8]}...`\n"
        keyboard = [[InlineKeyboardButton("🔄 Обновить", callback_data="refresh_tenants")]]
        await query.edit_message_text(text, parse_mode="Markdown", reply_markup=InlineKeyboardMarkup(keyboard))
    except Exception as e:
        await query.edit_message_text(f"❌ Ошибка: {e}")


async def cmd_help(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "📖 *Справка по боту*\n\n"
        "/start — Регистрация и активация уведомлений\n"
        "/stats — Платформенная аналитика (MRR, студенты, тарифы)\n"
        "/tenants — Список последних УЦ\n"
        "/uc <id> — Детали УЦ + кнопки блокировки и смены тарифа\n"
        "/help — Эта справка\n\n"
        "🔔 Автоматические уведомления:\n"
        "• Новая регистрация УЦ\n"
        "• Изменение тарифа\n"
        "• Истечение пробного периода",
        parse_mode="Markdown",
    )


# ─── Helpers ──────────────────────────────────────────────────────────────────

def _fmt_money(val) -> str:
    if val is None:
        return "—"
    try:
        return f"{int(float(val)):,}₸".replace(",", " ")
    except Exception:
        return str(val)


def _status_icon(status: str) -> str:
    return {
        "ACTIVE": "🟢",
        "TRIAL": "🔵",
        "SUSPENDED": "🟡",
        "BANNED": "🔴",
        "INACTIVE": "⚫",
        "TRIAL_EXPIRED": "🟠",
    }.get(status, "⚪")


# ─── FastAPI + Telegram Application ──────────────────────────────────────────

tg_app: Application | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global tg_app
    tg_app = (
        Application.builder()
        .token(BOT_TOKEN)
        .build()
    )

    tg_app.add_handler(CommandHandler("start", cmd_start))
    tg_app.add_handler(CommandHandler("stats", cmd_stats))
    tg_app.add_handler(CommandHandler("tenants", cmd_tenants))
    tg_app.add_handler(CommandHandler("uc", cmd_uc))
    tg_app.add_handler(CommandHandler("help", cmd_help))
    tg_app.add_handler(CallbackQueryHandler(on_callback))

    await tg_app.initialize()
    await tg_app.start()

    # Start polling in background
    asyncio.create_task(tg_app.updater.start_polling(drop_pending_updates=True))

    logger.info("Telegram bot started (polling)")
    yield

    await tg_app.updater.stop()
    await tg_app.stop()
    await tg_app.shutdown()


web_app = FastAPI(lifespan=lifespan)


@web_app.post("/internal/telegram/notify")
async def notify(request: Request):
    """
    Принимает уведомления от бэкенда и рассылает всем зарегистрированным chat_id.
    Body: { "type": "NEW_TENANT" | "PLAN_CHANGED" | "TRIAL_EXPIRED" | "CUSTOM",
            "tenantId": "...", "tenantName": "...", "plan": "...", "oldPlan": "...",
            "message": "..." }
    """
    body = await request.json()
    event_type = body.get("type", "CUSTOM")
    tenant_id = body.get("tenantId", "")
    tenant_name = body.get("tenantName", "")
    plan = body.get("plan", "")
    old_plan = body.get("oldPlan", "")
    custom_msg = body.get("message", "")

    if event_type == "NEW_TENANT":
        text = (
            f"🆕 *Новая регистрация УЦ*\n\n"
            f"🏢 {tenant_name}\n"
            f"ID: `{tenant_id}`\n"
            f"Тариф: {plan}"
        )
        keyboard = InlineKeyboardMarkup([
            [
                InlineKeyboardButton("🚫 Забанить", callback_data=f"ban:{tenant_id}"),
                InlineKeyboardButton("📋 Сменить тариф", callback_data=f"info:{tenant_id}"),
            ]
        ])
    elif event_type == "PLAN_CHANGED":
        text = (
            f"📋 *Смена тарифа*\n\n"
            f"🏢 {tenant_name}\n"
            f"ID: `{tenant_id}`\n"
            f"{old_plan} → *{plan}*"
        )
        keyboard = None
    elif event_type == "TRIAL_EXPIRED":
        text = (
            f"⏰ *Пробный период истёк*\n\n"
            f"🏢 {tenant_name}\n"
            f"ID: `{tenant_id}`"
        )
        keyboard = InlineKeyboardMarkup([
            [InlineKeyboardButton("💳 Активировать подписку", callback_data=f"activate:{tenant_id}:BASIC:MONTHLY")]
        ])
    else:
        text = custom_msg or f"📢 Уведомление: {event_type}"
        keyboard = None

    if not tg_app:
        raise HTTPException(status_code=503, detail="Bot not ready")

    chat_ids = store.all_ids()
    if not chat_ids:
        return {"status": "no_recipients"}

    sent = 0
    for cid in chat_ids:
        try:
            await tg_app.bot.send_message(cid, text, parse_mode="Markdown", reply_markup=keyboard)
            sent += 1
        except Exception as e:
            logger.warning("Failed to send to %s: %s", cid, e)

    return {"status": "ok", "sent": sent}


@web_app.get("/health")
async def health():
    return {"status": "UP"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("bot:web_app", host="0.0.0.0", port=8140, reload=False)
