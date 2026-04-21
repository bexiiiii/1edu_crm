CREATE OR REPLACE FUNCTION system.ensure_inventory_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    -- Inventory Categories (настраиваемые категории)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_categories'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.inventory_categories (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                branch_id UUID,
                name VARCHAR(100) NOT NULL,
                description TEXT,
                icon VARCHAR(50),
                is_system BOOLEAN DEFAULT FALSE,
                is_active BOOLEAN DEFAULT TRUE,
                sort_order INTEGER DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP
            )
        ', t_schema);

        EXECUTE format('CREATE INDEX idx_inv_categories_branch ON %I.inventory_categories (branch_id)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_categories_active ON %I.inventory_categories (is_active)', t_schema);
    END IF;

    -- Units of Measurement (единицы измерения)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_units'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.inventory_units (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                branch_id UUID,
                name VARCHAR(50) NOT NULL,
                abbreviation VARCHAR(10) NOT NULL,
                unit_type VARCHAR(20) NOT NULL, -- piece, weight, length, volume, area
                description TEXT,
                is_system BOOLEAN DEFAULT FALSE,
                is_active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
        ', t_schema);

        EXECUTE format('CREATE INDEX idx_inv_units_branch ON %I.inventory_units (branch_id)', t_schema);
    END IF;

    -- Inventory Items (товары/материалы)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_items'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.inventory_items (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                branch_id UUID,
                category_id UUID REFERENCES %I.inventory_categories(id),
                unit_id UUID REFERENCES %I.inventory_units(id),

                -- Identification
                sku VARCHAR(50),                  -- Артикул/SKU
                barcode VARCHAR(100),              -- Штрихкод
                name VARCHAR(200) NOT NULL,
                description TEXT,
                brand VARCHAR(100),                -- Бренд/производитель
                model VARCHAR(100),                -- Модель

                -- Quantity & Pricing
                quantity DECIMAL(15, 3) NOT NULL DEFAULT 0,  -- Текущее количество (decimal для кг, литров)
                min_quantity DECIMAL(15, 3),                 -- Минимальное количество (для алертов)
                max_quantity DECIMAL(15, 3),                 -- Максимальное количество
                price_per_unit DECIMAL(15, 2),               -- Закупочная цена
                selling_price DECIMAL(15, 2),                -- Цена продажи (если применимо)
                currency VARCHAR(3) DEFAULT ''UZS'',

                -- Location & Storage
                location VARCHAR(100),             -- Где хранится (кабинет, склад, полка)
                supplier VARCHAR(200),             -- Поставщик
                supplier_contact VARCHAR(100),     -- Контакт поставщика

                -- Status & Tracking
                status VARCHAR(30) DEFAULT ''IN_STOCK'',  -- IN_STOCK, LOW_STOCK, OUT_OF_STOCK, DISCONTINUED, ON_ORDER
                is_active BOOLEAN DEFAULT TRUE,
                is_tracked BOOLEAN DEFAULT TRUE,   -- Отслеживать ли остатки
                requires_reorder BOOLEAN DEFAULT FALSE,  -- Нужно ли заказывать

                -- Media & Attachments
                image_url TEXT,

                -- Audit
                created_by UUID,
                updated_by UUID,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP,
                notes TEXT
            )
        ', t_schema, t_schema);

        EXECUTE format('CREATE INDEX idx_inv_items_branch ON %I.inventory_items (branch_id)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_items_category ON %I.inventory_items (category_id)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_items_sku ON %I.inventory_items (sku)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_items_barcode ON %I.inventory_items (barcode)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_items_status ON %I.inventory_items (status)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_items_active ON %I.inventory_items (is_active)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_items_low_stock ON %I.inventory_items (requires_reorder)', t_schema);
    END IF;

    -- Inventory Transactions (движение товаров)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_transactions'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.inventory_transactions (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                branch_id UUID,
                item_id UUID NOT NULL REFERENCES %I.inventory_items(id),

                -- Transaction details
                transaction_type VARCHAR(30) NOT NULL,  -- RECEIVED, ISSUED, RETURNED, ADJUSTMENT, WRITE_OFF, TRANSFER
                quantity DECIMAL(15, 3) NOT NULL,       -- Количество (+ для поступления, - для расхода)
                quantity_before DECIMAL(15, 3),         -- Количество до операции
                quantity_after DECIMAL(15, 3),          -- Количество после операции

                -- Reference & Tracking
                reference_type VARCHAR(50),             -- purchase_order, issue_request, return, adjustment, transfer
                reference_id UUID,                      -- ID связанного документа
                reference_number VARCHAR(50),           -- Номер документа (накладная, акт)

                -- People & Responsibility
                performed_by UUID,                      -- Кто сделал
                approved_by UUID,                       -- Кто утвердил (для списаний)
                recipient_id UUID,                      -- Кто получил (для выдачи)

                -- Financial
                unit_cost DECIMAL(15, 2),               -- Себестоимость единицы на момент операции
                total_cost DECIMAL(15, 2),              -- Общая стоимость

                -- Timing & Notes
                transaction_date TIMESTAMP NOT NULL DEFAULT NOW(),
                notes TEXT,
                reason TEXT,                            -- Причина (для списаний/корректировок)

                created_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
        ', t_schema, t_schema);

        EXECUTE format('CREATE INDEX idx_inv_trans_branch ON %I.inventory_transactions (branch_id)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_trans_item ON %I.inventory_transactions (item_id)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_trans_type ON %I.inventory_transactions (transaction_type)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_trans_date ON %I.inventory_transactions (transaction_date)', t_schema);
        EXECUTE format('CREATE INDEX idx_inv_trans_reference ON %I.inventory_transactions (reference_type, reference_id)', t_schema);
    END IF;

    -- Insert system units if not exist
    INSERT INTO inventory_units (name, abbreviation, unit_type, is_system, is_active)
    VALUES
        ('Штука', 'шт', 'piece', true, true),
        ('Комплект', 'компл', 'piece', true, true),
        ('Коробка', 'кор', 'piece', true, true),
        ('Упаковка', 'упак', 'piece', true, true),
        ('Килограмм', 'кг', 'weight', true, true),
        ('Грамм', 'г', 'weight', true, true),
        ('Литр', 'л', 'volume', true, true),
        ('Миллилитр', 'мл', 'volume', true, true),
        ('Метр', 'м', 'length', true, true),
        ('Сантиметр', 'см', 'length', true, true),
        ('Квадратный метр', 'м²', 'area', true, true),
        ('Рулон', 'рул', 'piece', true, true),
        ('Лист', 'лист', 'piece', true, true),
        ('Бутылка', 'бут', 'piece', true, true),
        ('Пачка', 'пач', 'piece', true, true),
        ('Банка', 'бан', 'piece', true, true),
        ('Тюбик', 'тюб', 'piece', true, true)
    ON CONFLICT DO NOTHING;

    -- Insert system categories if not exist
    INSERT INTO inventory_categories (name, description, icon, is_system, is_active, sort_order)
    VALUES
        ('Учебники', 'Учебники и учебные пособия', '📚', true, true, 1),
        ('Рабочие тетради', 'Рабочие тетради и задачники', '📝', true, true, 2),
        ('Канцтовары', 'Ручки, карандаши, ластики и т.д.', '✏️', true, true, 3),
        ('Бумага и картон', 'Бумага для принтера, картон, ватман', '📄', true, true, 4),
        ('Оборудование', 'Компьютеры, проекторы, принтеры', '💻', true, true, 5),
        ('Мебель', 'Парты, стулья, шкафы', '🪑', true, true, 6),
        ('Расходные материалы', 'Картриджи, батареи, лампочки', '🔧', true, true, 7),
        ('Хозяйственные товары', 'Моющие средства, салфетки', '🧹', true, true, 8),
        ('Электроника', 'Флешки, кабели, зарядки', '🔌', true, true, 9),
        ('Спортинвентарь', 'Мячи, ракетки, ковры', '⚽', true, true, 10),
        ('Другое', 'Прочие товары', '📦', true, true, 99)
    ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
    LOOP
        PERFORM system.ensure_inventory_schema(t_schema);
    END LOOP;
END $$;
