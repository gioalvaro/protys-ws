-- ============================================================================
-- ODOO 16.x SCHEMA FOR PROTYS-WS PAINT CASE STUDY
-- ============================================================================
-- SQL schema for Odoo 16 PostgreSQL database with sample paint
-- manufacturing data (TiO2, Acrylic Resin, PearlMill_01 operations)
-- ============================================================================

-- Create schema
CREATE SCHEMA IF NOT EXISTS odoo;
SET search_path TO odoo, public;

-- ============================================================================
-- PRODUCT MANAGEMENT TABLES
-- ============================================================================

-- Product Categories
CREATE TABLE product_category (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id INT REFERENCES product_category(id),
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_product_category_parent ON product_category(parent_id);
CREATE INDEX idx_product_category_active ON product_category(active);

-- Units of Measure
CREATE TABLE uom_uom (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category_id INT,
    factor_inv DECIMAL(19,6) DEFAULT 1,
    active BOOLEAN DEFAULT true
);

-- Product Templates (Base Product Information)
CREATE TABLE product_template (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    categ_id INT NOT NULL REFERENCES product_category(id),
    type VARCHAR(20) DEFAULT 'product', -- 'consu' for consumable/raw, 'product' for storable
    description TEXT,
    default_code VARCHAR(255),
    barcode VARCHAR(255),
    uom_id INT REFERENCES uom_uom(id),
    uom_po_id INT REFERENCES uom_uom(id),
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_product_template_categ ON product_template(categ_id);
CREATE INDEX idx_product_template_active ON product_template(active);
CREATE INDEX idx_product_template_default_code ON product_template(default_code);

-- Product Products (Product Variants)
CREATE TABLE product_product (
    id SERIAL PRIMARY KEY,
    product_tmpl_id INT NOT NULL REFERENCES product_template(id),
    name VARCHAR(255),
    default_code VARCHAR(255),
    barcode VARCHAR(255),
    active BOOLEAN DEFAULT true,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_product_template ON product_product(product_tmpl_id);
CREATE INDEX idx_product_product_active ON product_product(active);
CREATE INDEX idx_product_product_default_code ON product_product(default_code);

-- ============================================================================
-- ORGANIZATIONAL STRUCTURE
-- ============================================================================

-- Companies
CREATE TABLE res_company (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id INT REFERENCES res_company(id),
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_res_company_parent ON res_company(parent_id);
CREATE INDEX idx_res_company_active ON res_company(active);

-- ============================================================================
-- WAREHOUSE AND STOCK MANAGEMENT
-- ============================================================================

-- Stock Warehouse
CREATE TABLE stock_warehouse (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255),
    company_id INT NOT NULL REFERENCES res_company(id),
    active BOOLEAN DEFAULT true,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_warehouse_company ON stock_warehouse(company_id);
CREATE INDEX idx_stock_warehouse_active ON stock_warehouse(active);

-- Stock Locations
CREATE TABLE stock_location (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    location_id INT REFERENCES stock_location(id),
    warehouse_id INT REFERENCES stock_warehouse(id),
    usage VARCHAR(20), -- 'internal', 'customer', 'supplier', 'transit'
    active BOOLEAN DEFAULT true,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_location_parent ON stock_location(location_id);
CREATE INDEX idx_stock_location_warehouse ON stock_location(warehouse_id);

-- Stock Quant (Inventory Quantities)
CREATE TABLE stock_quant (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL REFERENCES product_product(id),
    location_id INT NOT NULL REFERENCES stock_location(id),
    company_id INT NOT NULL REFERENCES res_company(id),
    quantity DECIMAL(19,6) DEFAULT 0,
    reserved_quantity DECIMAL(19,6) DEFAULT 0,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_quant_product ON stock_quant(product_id);
CREATE INDEX idx_stock_quant_location ON stock_quant(location_id);
CREATE INDEX idx_stock_quant_company ON stock_quant(company_id);
CREATE INDEX idx_stock_quant_quantity ON stock_quant(quantity);

-- ============================================================================
-- BILL OF MATERIALS
-- ============================================================================

-- MRP BOM (Bill of Materials)
CREATE TABLE mrp_bom (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL REFERENCES product_product(id),
    code VARCHAR(255),
    type VARCHAR(20) DEFAULT 'normal', -- 'normal', 'phantom'
    active BOOLEAN DEFAULT true,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mrp_bom_product ON mrp_bom(product_id);
CREATE INDEX idx_mrp_bom_active ON mrp_bom(active);

-- MRP BOM Lines (Components)
CREATE TABLE mrp_bom_line (
    id SERIAL PRIMARY KEY,
    bom_id INT NOT NULL REFERENCES mrp_bom(id),
    product_id INT NOT NULL REFERENCES product_product(id),
    product_qty DECIMAL(19,6) NOT NULL,
    product_uom_id INT NOT NULL REFERENCES uom_uom(id),
    sequence INT,
    active BOOLEAN DEFAULT true,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mrp_bom_line_bom ON mrp_bom_line(bom_id);
CREATE INDEX idx_mrp_bom_line_product ON mrp_bom_line(product_id);
CREATE INDEX idx_mrp_bom_line_active ON mrp_bom_line(active);

-- ============================================================================
-- MANUFACTURING / PRODUCTION
-- ============================================================================

-- MRP Production (Manufacturing Orders)
CREATE TABLE mrp_production (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    product_id INT NOT NULL REFERENCES product_product(id),
    product_qty DECIMAL(19,6),
    qty_produced DECIMAL(19,6) DEFAULT 0,
    product_uom_id INT REFERENCES uom_uom(id),
    bom_id INT REFERENCES mrp_bom(id),
    date_planned_start TIMESTAMP,
    date_planned_finished TIMESTAMP,
    state VARCHAR(20) DEFAULT 'draft', -- 'draft', 'confirmed', 'progress', 'to_close', 'done', 'cancel'
    company_id INT NOT NULL REFERENCES res_company(id),
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_mrp_production_product ON mrp_production(product_id);
CREATE INDEX idx_mrp_production_company ON mrp_production(company_id);
CREATE INDEX idx_mrp_production_state ON mrp_production(state);
CREATE INDEX idx_mrp_production_active ON mrp_production(active);

-- MRP Work Orders (Manufacturing Operations)
CREATE TABLE mrp_workorder (
    id SERIAL PRIMARY KEY,
    production_id INT NOT NULL REFERENCES mrp_production(id),
    name VARCHAR(255),
    workcenter_id INT,
    state VARCHAR(20) DEFAULT 'pending', -- 'pending', 'waiting', 'ready', 'progress', 'done', 'cancel'
    date_start TIMESTAMP,
    date_finished TIMESTAMP,
    qty_producing DECIMAL(19,6),
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    write_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT true
);

CREATE INDEX idx_mrp_workorder_production ON mrp_workorder(production_id);
CREATE INDEX idx_mrp_workorder_state ON mrp_workorder(state);
CREATE INDEX idx_mrp_workorder_active ON mrp_workorder(active);

-- ============================================================================
-- SAMPLE DATA - PAINT MANUFACTURING CASE STUDY
-- ============================================================================

-- Insert product categories
INSERT INTO product_category (name, parent_id) VALUES
    ('Products', NULL),
    ('Raw Materials', 1),
    ('Pigments & Additives', 1),
    ('Resin & Binders', 1);

-- Insert units of measure
INSERT INTO uom_uom (name, factor_inv) VALUES
    ('kg', 1.0),
    ('liter', 1.0),
    ('unit', 1.0),
    ('ton', 0.001),
    ('ml', 1000.0);

-- Insert raw materials and components
INSERT INTO product_template (name, categ_id, type, description, default_code, uom_id) VALUES
    ('TiO2 (Titanium Dioxide)', 3, 'consu', 'White pigment for paint formulation', 'TIO2-001', 1),
    ('Acrylic Resin', 4, 'consu', 'Synthetic resin binder for water-based paints', 'ACRY-RESIN-001', 2),
    ('Mineral Oil', 3, 'consu', 'Solvent for paint formulation', 'MIN-OIL-001', 2),
    ('PearlMill_01 Pearl Pigment', 3, 'consu', 'Pearlescent pigment for special effects', 'PEARL-001', 1),
    ('Thickener', 3, 'consu', 'Viscosity modifier for paint', 'THICK-001', 2),
    ('Hardener', 3, 'consu', 'Cross-linking agent for durability', 'HARD-001', 2),
    ('Water (Distilled)', 3, 'consu', 'Distilled water for aqueous paints', 'WATER-001', 2);

-- Insert finished paint products
INSERT INTO product_template (name, categ_id, type, description, default_code, uom_id) VALUES
    ('Premium Acrylic Paint - White', 1, 'product', 'High-quality white acrylic paint for interior walls', 'PAI-WHITE-001', 3),
    ('Pearl Effect Paint - Silver', 1, 'product', 'Metallic silver paint with pearlescent finish', 'PAI-PEARL-001', 3),
    ('Industrial Grade Paint - Gray', 1, 'product', 'Industrial-grade gray protective coating', 'PAI-GRAY-001', 3);

-- Insert product variants (products)
INSERT INTO product_product (product_tmpl_id, name, default_code) VALUES
    (1, 'TiO2 (Titanium Dioxide)', 'TIO2-001'),
    (2, 'Acrylic Resin', 'ACRY-RESIN-001'),
    (3, 'Mineral Oil', 'MIN-OIL-001'),
    (4, 'PearlMill_01 Pearl Pigment', 'PEARL-001'),
    (5, 'Thickener', 'THICK-001'),
    (6, 'Hardener', 'HARD-001'),
    (7, 'Water (Distilled)', 'WATER-001'),
    (8, 'Premium Acrylic Paint - White', 'PAI-WHITE-001'),
    (9, 'Pearl Effect Paint - Silver', 'PAI-PEARL-001'),
    (10, 'Industrial Grade Paint - Gray', 'PAI-GRAY-001');

-- Insert companies
INSERT INTO res_company (name, parent_id) VALUES
    ('PaintCorp Manufacturing', NULL),
    ('Quality Control Department', 1),
    ('Distribution Center', 1);

-- Insert stock warehouse
INSERT INTO stock_warehouse (name, code, company_id) VALUES
    ('PaintCorp Main Warehouse', 'WAR-01', 1);

-- Insert stock locations
INSERT INTO stock_location (name, warehouse_id, usage) VALUES
    ('Raw Materials Rack A', 1, 'internal'),
    ('Raw Materials Rack B', 1, 'internal'),
    ('Work-in-Progress Area', 1, 'internal'),
    ('Finished Goods Area', 1, 'internal'),
    ('Quality Control Hold', 1, 'internal');

-- Insert stock quantities (inventory)
INSERT INTO stock_quant (product_id, location_id, company_id, quantity, reserved_quantity) VALUES
    (1, 1, 1, 1000.00, 0.00),    -- 1000 kg TiO2
    (2, 1, 1, 500.00, 0.00),     -- 500 liters Acrylic Resin
    (3, 2, 1, 300.00, 0.00),     -- 300 liters Mineral Oil
    (4, 1, 1, 50.00, 0.00),      -- 50 kg Pearl Pigment
    (5, 2, 1, 100.00, 0.00),     -- 100 liters Thickener
    (6, 2, 1, 75.00, 0.00),      -- 75 liters Hardener
    (7, 1, 1, 2000.00, 0.00),    -- 2000 liters Water
    (8, 4, 1, 250.00, 0.00),     -- 250 units White Paint
    (9, 4, 1, 100.00, 0.00),     -- 100 units Pearl Paint
    (10, 4, 1, 150.00, 0.00);    -- 150 units Gray Paint

-- Insert BOMs (Bill of Materials) for paint products

-- BOM for Premium Acrylic Paint - White
INSERT INTO mrp_bom (product_id, code, type) VALUES
    (8, 'BOM-PAI-WHITE-001', 'normal');

INSERT INTO mrp_bom_line (bom_id, product_id, product_qty, product_uom_id, sequence) VALUES
    (1, 1, 200.00, 1, 10),   -- 200 kg TiO2
    (1, 2, 400.00, 2, 20),   -- 400 liters Acrylic Resin
    (1, 3, 150.00, 2, 30),   -- 150 liters Mineral Oil
    (1, 5, 50.00, 2, 40),    -- 50 liters Thickener
    (1, 6, 25.00, 2, 50),    -- 25 liters Hardener
    (1, 7, 175.00, 2, 60);   -- 175 liters Water

-- BOM for Pearl Effect Paint - Silver
INSERT INTO mrp_bom (product_id, code, type) VALUES
    (9, 'BOM-PAI-PEARL-001', 'normal');

INSERT INTO mrp_bom_line (bom_id, product_id, product_qty, product_uom_id, sequence) VALUES
    (2, 1, 150.00, 1, 10),   -- 150 kg TiO2 base
    (2, 4, 40.00, 1, 15),    -- 40 kg PearlMill_01
    (2, 2, 380.00, 2, 20),   -- 380 liters Acrylic Resin
    (2, 3, 140.00, 2, 30),   -- 140 liters Mineral Oil
    (2, 5, 45.00, 2, 40),    -- 45 liters Thickener
    (2, 6, 30.00, 2, 50),    -- 30 liters Hardener
    (2, 7, 165.00, 2, 60);   -- 165 liters Water

-- BOM for Industrial Grade Paint - Gray
INSERT INTO mrp_bom (product_id, code, type) VALUES
    (10, 'BOM-PAI-GRAY-001', 'normal');

INSERT INTO mrp_bom_line (bom_id, product_id, product_qty, product_uom_id, sequence) VALUES
    (3, 1, 180.00, 1, 10),   -- 180 kg TiO2
    (3, 2, 420.00, 2, 20),   -- 420 liters Acrylic Resin
    (3, 3, 160.00, 2, 30),   -- 160 liters Mineral Oil
    (3, 5, 60.00, 2, 40),    -- 60 liters Thickener
    (3, 6, 35.00, 2, 50),    -- 35 liters Hardener
    (3, 7, 145.00, 2, 60);   -- 145 liters Water

-- Insert production orders

-- Production batch 1: White Acrylic Paint
INSERT INTO mrp_production (name, product_id, product_qty, qty_produced, product_uom_id, bom_id, date_planned_start, date_planned_finished, state, company_id) VALUES
    ('MO/2024/001', 8, 1000.00, 950.00, 3, 1, '2024-01-15 08:00:00', '2024-01-18 16:00:00', 'done', 1);

INSERT INTO mrp_workorder (production_id, name, state, date_start, date_finished, qty_producing) VALUES
    (1, 'WO-001-01: Mixing pigments', 'done', '2024-01-15 08:00:00', '2024-01-15 10:00:00', 1000.00),
    (1, 'WO-001-02: Adding resin binder', 'done', '2024-01-15 10:30:00', '2024-01-15 12:00:00', 1000.00),
    (1, 'WO-001-03: Milling and quality check', 'done', '2024-01-15 13:00:00', '2024-01-18 16:00:00', 950.00);

-- Production batch 2: Pearl Effect Paint
INSERT INTO mrp_production (name, product_id, product_qty, qty_produced, product_uom_id, bom_id, date_planned_start, date_planned_finished, state, company_id) VALUES
    ('MO/2024/002', 9, 500.00, 480.00, 3, 2, '2024-01-20 10:00:00', '2024-01-22 16:00:00', 'done', 1);

INSERT INTO mrp_workorder (production_id, name, state, date_start, date_finished, qty_producing) VALUES
    (2, 'WO-002-01: Mixing base with PearlMill_01', 'done', '2024-01-20 10:00:00', '2024-01-20 12:00:00', 500.00),
    (2, 'WO-002-02: Adding acrylic resin', 'done', '2024-01-20 13:00:00', '2024-01-20 14:30:00', 500.00),
    (2, 'WO-002-03: Blending and quality assurance', 'done', '2024-01-20 15:00:00', '2024-01-22 16:00:00', 480.00);

-- Production batch 3: Industrial Paint (In Progress)
INSERT INTO mrp_production (name, product_id, product_qty, qty_produced, product_uom_id, bom_id, date_planned_start, date_planned_finished, state, company_id) VALUES
    ('MO/2024/003', 10, 800.00, 0.00, 3, 3, '2024-02-01 09:00:00', '2024-02-05 17:00:00', 'progress', 1);

INSERT INTO mrp_workorder (production_id, name, state, date_start, date_finished, qty_producing) VALUES
    (3, 'WO-003-01: Preparing gray pigment mixture', 'progress', '2024-02-01 09:00:00', NULL, 800.00),
    (3, 'WO-003-02: Batch preparation', 'pending', NULL, NULL, NULL);

-- ============================================================================
-- INDEXES FOR PERFORMANCE
-- ============================================================================

CREATE INDEX idx_product_template_default_code_active ON product_template(default_code, active);
CREATE INDEX idx_mrp_bom_product_active ON mrp_bom(product_id, active);
CREATE INDEX idx_mrp_production_company_state ON mrp_production(company_id, state);
CREATE INDEX idx_stock_quant_product_location ON stock_quant(product_id, location_id);

-- ============================================================================
-- GRANTS (if using specific user)
-- ============================================================================

GRANT ALL PRIVILEGES ON SCHEMA odoo TO protys_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA odoo TO protys_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA odoo TO protys_user;

COMMIT;
