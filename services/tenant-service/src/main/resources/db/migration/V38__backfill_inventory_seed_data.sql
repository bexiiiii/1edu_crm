-- Backfill inventory seed data (units and categories) for existing tenants
-- that had tables created by V31 but without seed data (ON CONFLICT had no UNIQUE constraint).

CREATE OR REPLACE FUNCTION system.backfill_inventory_seed_data(t_schema TEXT)
RETURNS void AS $$
DECLARE
    unit_count INT;
    cat_count INT;
BEGIN
    -- Only backfill if tables exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_units'
    ) THEN
        RETURN;
    END IF;

    -- Insert system units if table is empty
    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_units WHERE is_system = true', t_schema) INTO unit_count;
    IF unit_count = 0 THEN
        EXECUTE format('
            INSERT INTO %I.inventory_units (name, abbreviation, unit_type, is_system, is_active)
            VALUES
                (''Штука'', ''шт'', ''piece'', true, true),
                (''Комплект'', ''компл'', ''piece'', true, true),
                (''Коробка'', ''кор'', ''piece'', true, true),
                (''Упаковка'', ''упак'', ''piece'', true, true),
                (''Килограмм'', ''кг'', ''weight'', true, true),
                (''Грамм'', ''г'', ''weight'', true, true),
                (''Литр'', ''л'', ''volume'', true, true),
                (''Миллилитр'', ''мл'', ''volume'', true, true),
                (''Метр'', ''м'', ''length'', true, true),
                (''Сантиметр'', ''см'', ''length'', true, true),
                (''Квадратный метр'', ''м²'', ''area'', true, true),
                (''Рулон'', ''рул'', ''piece'', true, true),
                (''Лист'', ''лист'', ''piece'', true, true),
                (''Бутылка'', ''бут'', ''piece'', true, true),
                (''Пачка'', ''пач'', ''piece'', true, true),
                (''Банка'', ''бан'', ''piece'', true, true),
                (''Тюбик'', ''тюб'', ''piece'', true, true)
        ', t_schema);
    END IF;

    -- Insert system categories if table is empty
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_categories'
    ) THEN
        RETURN;
    END IF;

    EXECUTE format('SELECT COUNT(*) FROM %I.inventory_categories WHERE is_system = true', t_schema) INTO cat_count;
    IF cat_count = 0 THEN
        EXECUTE format('
            INSERT INTO %I.inventory_categories (name, description, icon, is_system, is_active, sort_order)
            VALUES
                (''Учебники'', ''Учебники и учебные пособия'', ''📚'', true, true, 1),
                (''Рабочие тетради'', ''Рабочие тетради и задачники'', ''📝'', true, true, 2),
                (''Канцтовары'', ''Ручки, карандаши, ластики и т.д.'', ''✏️'', true, true, 3),
                (''Бумага и картон'', ''Бумага для принтера, картон, ватман'', ''📄'', true, true, 4),
                (''Оборудование'', ''Компьютеры, проекторы, принтеры'', ''💻'', true, true, 5),
                (''Мебель'', ''Парты, стулья, шкафы'', ''🪑'', true, true, 6),
                (''Расходные материалы'', ''Картриджи, батареи, лампочки'', ''🔧'', true, true, 7),
                (''Хозяйственные товары'', ''Моющие средства, салфетки'', ''🧹'', true, true, 8),
                (''Электроника'', ''Флешки, кабели, зарядки'', ''🔌'', true, true, 9),
                (''Спортинвентарь'', ''Мячи, ракетки, ковры'', ''⚽'', true, true, 10),
                (''Другое'', ''Прочие товары'', ''📦'', true, true, 99)
        ', t_schema);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenants
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
          AND deleted_at IS NULL
    LOOP
        PERFORM system.backfill_inventory_seed_data(t_schema);
    END LOOP;
END $$;
