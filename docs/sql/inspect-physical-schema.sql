-- Read-only inspection after applying the design to an isolated MySQL schema.
-- Select that schema in the SQL client before running these queries.
SELECT DATABASE() AS selected_schema, VERSION() AS mysql_version,
       @@session.time_zone AS session_time_zone, @@session.sql_mode AS sql_mode;

SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE,
       IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;

SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME,
       REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, COLLATION
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
ORDER BY CONSTRAINT_NAME;

-- V1: 16 application tables; V2 top-up extension: 17. Flyway metadata is excluded.
SELECT COUNT(*) AS application_table_count,
       16 + EXISTS(SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_topups') AS expected_application_table_count
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME <> 'flyway_schema_history';

-- Price/duration remain in orders; immutable text/version/requirements belong to snapshots.
SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('orders', 'order_snapshots')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- Must return zero rows: no snapshot fields should remain in orders after the split.
SELECT COLUMN_NAME AS unexpectedly_retained_snapshot_column
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
  AND COLUMN_NAME IN ('product_version_snapshot', 'product_title_snapshot',
      'description_snapshot', 'scope_snapshot', 'preparation_snapshot',
      'terms_snapshot', 'requirements');

-- Must return zero rows: every order needs its snapshot after a transaction commits.
SELECT o.id AS order_without_snapshot
FROM orders AS o
LEFT JOIN order_snapshots AS s ON s.order_id = o.id
WHERE s.order_id IS NULL;

-- Must return zero rows: the FK also prevents snapshots without a parent order.
SELECT s.order_id AS snapshot_without_order
FROM order_snapshots AS s
LEFT JOIN orders AS o ON o.id = s.order_id
WHERE o.id IS NULL;
