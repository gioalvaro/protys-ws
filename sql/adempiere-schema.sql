-- ============================================================================
-- ADEMPIERE 4.x SCHEMA FOR PROTYS-WS PAINT CASE STUDY
-- ============================================================================
-- SQL schema for ADempiere PostgreSQL database with sample paint
-- manufacturing data (TiO2, Acrylic Resin, PearlMill_01 operations)
-- ============================================================================

-- Create schema
CREATE SCHEMA IF NOT EXISTS adempiere;
SET search_path TO adempiere, public;

-- ============================================================================
-- PRODUCT MANAGEMENT TABLES
-- ============================================================================

-- Product Categories
CREATE TABLE M_Product_Category (
    M_Product_Category_ID SERIAL PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    Description TEXT,
    IsActive CHAR(1) DEFAULT 'Y'
);

-- Products (Raw Materials and Final Products)
CREATE TABLE M_Product (
    M_Product_ID SERIAL PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    Description TEXT,
    Value VARCHAR(100),
    M_Product_Category_ID INT REFERENCES M_Product_Category(M_Product_Category_ID),
    ProductType VARCHAR(20), -- 'M' for Material, 'P' for Product
    CreatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UpdatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_product_category ON M_Product(M_Product_Category_ID);
CREATE INDEX idx_m_product_active ON M_Product(IsActive);

-- ============================================================================
-- ORGANIZATIONAL STRUCTURE
-- ============================================================================

-- Organizations (Companies, Plants)
CREATE TABLE AD_Org (
    AD_Org_ID SERIAL PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    Description TEXT,
    AD_Org_Parent_ID INT REFERENCES AD_Org(AD_Org_ID),
    IsActive CHAR(1) DEFAULT 'Y',
    CreatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ad_org_parent ON AD_Org(AD_Org_Parent_ID);
CREATE INDEX idx_ad_org_active ON AD_Org(IsActive);

-- ============================================================================
-- WAREHOUSE AND INVENTORY
-- ============================================================================

-- Warehouses
CREATE TABLE M_Warehouse (
    M_Warehouse_ID SERIAL PRIMARY KEY,
    Name VARCHAR(255) NOT NULL,
    Description TEXT,
    AD_Org_ID INT NOT NULL REFERENCES AD_Org(AD_Org_ID),
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_warehouse_org ON M_Warehouse(AD_Org_ID);
CREATE INDEX idx_m_warehouse_active ON M_Warehouse(IsActive);

-- Locators (Storage locations within warehouse)
CREATE TABLE M_Locator (
    M_Locator_ID SERIAL PRIMARY KEY,
    M_Warehouse_ID INT NOT NULL REFERENCES M_Warehouse(M_Warehouse_ID),
    Value VARCHAR(100),
    X VARCHAR(20),
    Y VARCHAR(20),
    Z VARCHAR(20),
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_locator_warehouse ON M_Locator(M_Warehouse_ID);

-- Inventory (Stock counts)
CREATE TABLE M_Inventory (
    M_Inventory_ID SERIAL PRIMARY KEY,
    M_Warehouse_ID INT NOT NULL REFERENCES M_Warehouse(M_Warehouse_ID),
    M_Product_ID INT NOT NULL REFERENCES M_Product(M_Product_ID),
    M_Locator_ID INT REFERENCES M_Locator(M_Locator_ID),
    MovementDate TIMESTAMP,
    QtyCount DECIMAL(19,6),
    Description TEXT,
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_inventory_warehouse ON M_Inventory(M_Warehouse_ID);
CREATE INDEX idx_m_inventory_product ON M_Inventory(M_Product_ID);

-- ============================================================================
-- BILL OF MATERIALS
-- ============================================================================

-- BOM (Bill of Materials)
CREATE TABLE M_BOM (
    M_BOM_ID SERIAL PRIMARY KEY,
    M_Product_ID INT NOT NULL REFERENCES M_Product(M_Product_ID),
    Name VARCHAR(255) NOT NULL,
    Description TEXT,
    BOMType VARCHAR(20), -- 'M' for Manufacturing, 'S' for Service
    BOMUse VARCHAR(20),
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_bom_product ON M_BOM(M_Product_ID);
CREATE INDEX idx_m_bom_active ON M_BOM(IsActive);

-- BOM Components
CREATE TABLE M_BOM_Component (
    M_BOM_Component_ID SERIAL PRIMARY KEY,
    M_BOM_ID INT NOT NULL REFERENCES M_BOM(M_BOM_ID),
    M_Product_ID INT NOT NULL REFERENCES M_Product(M_Product_ID),
    Line INT,
    BOMQty DECIMAL(19,6) NOT NULL,
    M_UOM_ID INT,
    Description TEXT,
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_bom_component_bom ON M_BOM_Component(M_BOM_ID);
CREATE INDEX idx_m_bom_component_product ON M_BOM_Component(M_Product_ID);

-- ============================================================================
-- PRODUCTION MANAGEMENT
-- ============================================================================

-- Production Orders
CREATE TABLE M_Production (
    M_Production_ID SERIAL PRIMARY KEY,
    M_Product_ID INT NOT NULL REFERENCES M_Product(M_Product_ID),
    DocumentNo VARCHAR(255),
    MovementDate TIMESTAMP,
    DatePromised TIMESTAMP,
    QtyOrdered DECIMAL(19,6),
    QtyDelivered DECIMAL(19,6),
    DocStatus VARCHAR(20), -- 'DR' Draft, 'IP' In Progress, 'CO' Completed
    AD_Org_ID INT NOT NULL REFERENCES AD_Org(AD_Org_ID),
    IsActive CHAR(1) DEFAULT 'Y',
    CreatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UpdatedDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_m_production_product ON M_Production(M_Product_ID);
CREATE INDEX idx_m_production_org ON M_Production(AD_Org_ID);
CREATE INDEX idx_m_production_active ON M_Production(IsActive);
CREATE INDEX idx_m_production_status ON M_Production(DocStatus);

-- Production Lines (steps in production)
CREATE TABLE M_ProductionLine (
    M_ProductionLine_ID SERIAL PRIMARY KEY,
    M_Production_ID INT NOT NULL REFERENCES M_Production(M_Production_ID),
    Line INT,
    M_Locator_ID INT REFERENCES M_Locator(M_Locator_ID),
    QtyBOM DECIMAL(19,6),
    Description TEXT,
    IsActive CHAR(1) DEFAULT 'Y'
);

CREATE INDEX idx_m_productionline_production ON M_ProductionLine(M_Production_ID);
CREATE INDEX idx_m_productionline_locator ON M_ProductionLine(M_Locator_ID);

-- ============================================================================
-- SAMPLE DATA - PAINT MANUFACTURING CASE STUDY
-- ============================================================================

-- Insert product categories
INSERT INTO M_Product_Category (Name, Description) VALUES
    ('Raw Materials', 'Raw materials for paint production'),
    ('Pigments & Additives', 'Pigments, fillers, and chemical additives'),
    ('Resin & Binders', 'Resins, binders, and film-forming materials'),
    ('Final Products', 'Finished paint products');

-- Insert raw materials and components
INSERT INTO M_Product (Name, Description, Value, M_Product_Category_ID, ProductType) VALUES
    ('TiO2 (Titanium Dioxide)', 'White pigment for paint formulation', 'TIO2-001', 2, 'M'),
    ('Acrylic Resin', 'Synthetic resin binder for water-based paints', 'ACRY-RESIN-001', 3, 'M'),
    ('Mineral Oil', 'Solvent for paint formulation', 'MIN-OIL-001', 2, 'M'),
    ('PearlMill_01 Pearl Pigment', 'Pearlescent pigment for special effects', 'PEARL-001', 2, 'M'),
    ('Thickener', 'Viscosity modifier for paint', 'THICK-001', 2, 'M'),
    ('Hardener', 'Cross-linking agent for durability', 'HARD-001', 2, 'M'),
    ('Water (Distilled)', 'Distilled water for aqueous paints', 'WATER-001', 2, 'M');

-- Insert finished paint products
INSERT INTO M_Product (Name, Description, Value, M_Product_Category_ID, ProductType) VALUES
    ('Premium Acrylic Paint - White', 'High-quality white acrylic paint for interior walls', 'PAI-WHITE-001', 4, 'P'),
    ('Pearl Effect Paint - Silver', 'Metallic silver paint with pearlescent finish', 'PAI-PEARL-001', 4, 'P'),
    ('Industrial Grade Paint - Gray', 'Industrial-grade gray protective coating', 'PAI-GRAY-001', 4, 'P');

-- Insert organizations (plants)
INSERT INTO AD_Org (Name, Description) VALUES
    ('PaintCorp Manufacturing', 'Main paint manufacturing plant'),
    ('Quality Control Department', 'QC operations for paint production'),
    ('Distribution Center', 'Finished goods distribution center');

-- Insert warehouses
INSERT INTO M_Warehouse (Name, Description, AD_Org_ID) VALUES
    ('Raw Materials Warehouse', 'Storage for raw materials and pigments', 1),
    ('Work-in-Progress Warehouse', 'Storage for intermediate production', 1),
    ('Finished Goods Warehouse', 'Storage for finished paint products', 1);

-- Insert locators
INSERT INTO M_Locator (M_Warehouse_ID, Value, X, Y, Z) VALUES
    (1, 'RM-A1', 'A', '1', '1'),
    (1, 'RM-A2', 'A', '2', '1'),
    (1, 'RM-B1', 'B', '1', '1'),
    (2, 'WIP-A1', 'A', '1', '1'),
    (3, 'FG-A1', 'A', '1', '1'),
    (3, 'FG-A2', 'A', '2', '1');

-- Insert inventory records (sample stock levels)
INSERT INTO M_Inventory (M_Warehouse_ID, M_Product_ID, M_Locator_ID, MovementDate, QtyCount) VALUES
    (1, 1, 1, CURRENT_TIMESTAMP, 1000.00),  -- 1000 kg TiO2
    (1, 2, 2, CURRENT_TIMESTAMP, 500.00),   -- 500 liters Acrylic Resin
    (1, 3, 3, CURRENT_TIMESTAMP, 300.00),   -- 300 liters Mineral Oil
    (1, 4, 1, CURRENT_TIMESTAMP, 50.00),    -- 50 kg Pearl Pigment
    (1, 5, 2, CURRENT_TIMESTAMP, 100.00),   -- 100 liters Thickener
    (1, 6, 3, CURRENT_TIMESTAMP, 75.00),    -- 75 liters Hardener
    (1, 7, 1, CURRENT_TIMESTAMP, 2000.00),  -- 2000 liters Water
    (3, 8, 5, CURRENT_TIMESTAMP, 250.00),   -- 250 units White Paint
    (3, 9, 6, CURRENT_TIMESTAMP, 100.00),   -- 100 units Pearl Paint
    (3, 10, 5, CURRENT_TIMESTAMP, 150.00);  -- 150 units Gray Paint

-- Insert BOMs (Bill of Materials) for paint products

-- BOM for Premium Acrylic Paint - White
INSERT INTO M_BOM (M_Product_ID, Name, Description, BOMType, BOMUse) VALUES
    (8, 'White Acrylic Paint BOM', 'Recipe for premium white acrylic paint', 'M', 'M');

INSERT INTO M_BOM_Component (M_BOM_ID, M_Product_ID, Line, BOMQty, Description) VALUES
    (1, 1, 10, 200.00, 'TiO2 Pigment'),
    (1, 2, 20, 400.00, 'Acrylic Resin Binder'),
    (1, 3, 30, 150.00, 'Mineral Oil Solvent'),
    (1, 5, 40, 50.00, 'Thickener'),
    (1, 6, 50, 25.00, 'Hardener'),
    (1, 7, 60, 175.00, 'Distilled Water');

-- BOM for Pearl Effect Paint - Silver
INSERT INTO M_BOM (M_Product_ID, Name, Description, BOMType, BOMUse) VALUES
    (9, 'Pearl Effect Paint BOM', 'Recipe for pearl effect silver paint', 'M', 'M');

INSERT INTO M_BOM_Component (M_BOM_ID, M_Product_ID, Line, BOMQty, Description) VALUES
    (2, 1, 10, 150.00, 'TiO2 Base Pigment'),
    (2, 4, 15, 40.00, 'PearlMill_01 Pearl Pigment'),
    (2, 2, 20, 380.00, 'Acrylic Resin Binder'),
    (2, 3, 30, 140.00, 'Mineral Oil Solvent'),
    (2, 5, 40, 45.00, 'Thickener'),
    (2, 6, 50, 30.00, 'Hardener'),
    (2, 7, 60, 165.00, 'Distilled Water');

-- BOM for Industrial Grade Paint - Gray
INSERT INTO M_BOM (M_Product_ID, Name, Description, BOMType, BOMUse) VALUES
    (10, 'Industrial Paint BOM', 'Recipe for industrial-grade gray protective paint', 'M', 'M');

INSERT INTO M_BOM_Component (M_BOM_ID, M_Product_ID, Line, BOMQty, Description) VALUES
    (3, 1, 10, 180.00, 'TiO2 for gray base'),
    (3, 2, 20, 420.00, 'Acrylic Resin Binder'),
    (3, 3, 30, 160.00, 'Mineral Oil Solvent'),
    (3, 5, 40, 60.00, 'Thickener'),
    (3, 6, 50, 35.00, 'Hardener'),
    (3, 7, 60, 145.00, 'Distilled Water');

-- Insert production orders

-- Production batch 1: White Acrylic Paint
INSERT INTO M_Production (M_Product_ID, DocumentNo, MovementDate, DatePromised, QtyOrdered, QtyDelivered, DocStatus, AD_Org_ID) VALUES
    (8, 'PROD-2024-001', '2024-01-15 08:00:00', '2024-01-18 16:00:00', 1000.00, 950.00, 'CO', 1);

INSERT INTO M_ProductionLine (M_Production_ID, Line, M_Locator_ID, QtyBOM, Description) VALUES
    (1, 1, 4, 200.00, 'Mixing TiO2 and Acrylic Resin'),
    (1, 2, 4, 150.00, 'Adding solvents and additives'),
    (1, 3, 4, 600.00, 'Milling and quality check');

-- Production batch 2: Pearl Effect Paint
INSERT INTO M_Production (M_Product_ID, DocumentNo, MovementDate, DatePromised, QtyOrdered, QtyDelivered, DocStatus, AD_Org_ID) VALUES
    (9, 'PROD-2024-002', '2024-01-20 10:00:00', '2024-01-22 16:00:00', 500.00, 480.00, 'CO', 1);

INSERT INTO M_ProductionLine (M_Production_ID, Line, M_Locator_ID, QtyBOM, Description) VALUES
    (2, 1, 4, 150.00, 'Mixing base pigments with PearlMill_01'),
    (2, 2, 4, 120.00, 'Adding acrylic resin'),
    (2, 3, 4, 230.00, 'Blending and quality assurance');

-- Production batch 3: Industrial Paint (In Progress)
INSERT INTO M_Production (M_Product_ID, DocumentNo, MovementDate, DatePromised, QtyOrdered, QtyDelivered, DocStatus, AD_Org_ID) VALUES
    (10, 'PROD-2024-003', '2024-02-01 09:00:00', '2024-02-05 17:00:00', 800.00, 0.00, 'IP', 1);

INSERT INTO M_ProductionLine (M_Production_ID, Line, M_Locator_ID, QtyBOM, Description) VALUES
    (3, 1, 4, 180.00, 'Preparing gray pigment mixture'),
    (3, 2, 4, 420.00, 'Batch preparation in progress');

-- ============================================================================
-- INDEXES FOR PERFORMANCE
-- ============================================================================

CREATE INDEX idx_m_product_value ON M_Product(Value);
CREATE INDEX idx_m_bom_component_active ON M_BOM_Component(IsActive);
CREATE INDEX idx_m_production_docstatus ON M_Production(DocStatus);
CREATE INDEX idx_m_inventory_product_warehouse ON M_Inventory(M_Product_ID, M_Warehouse_ID);

-- ============================================================================
-- GRANTS (if using specific user)
-- ============================================================================

GRANT ALL PRIVILEGES ON SCHEMA adempiere TO protys_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA adempiere TO protys_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA adempiere TO protys_user;

COMMIT;
