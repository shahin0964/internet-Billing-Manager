<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once 'db.php';

if (!function_exists('authenticateUser')) {
    function authenticateUser($pdo) {
        $token = null;
        $headers = getallheaders();
        if (isset($headers['Authorization'])) {
            if (preg_match('/Bearer\\s(\\S+)/', $headers['Authorization'], $matches)) {
                $token = $matches[1];
            }
        } elseif (isset($_SERVER['HTTP_AUTHORIZATION'])) {
            if (preg_match('/Bearer\\s(\\S+)/', $_SERVER['HTTP_AUTHORIZATION'], $matches)) {
                $token = $matches[1];
            }
        }

        if (empty($token)) {
            http_response_code(401);
            echo json_encode(["status" => false, "message" => "Unauthorized: Token missing."]);
            exit;
        }

        $stmt = $pdo->prepare("SELECT id FROM users WHERE api_token = ? LIMIT 1");
        $stmt->execute([$token]);
        $user = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$user) {
            http_response_code(401);
            echo json_encode(["status" => false, "message" => "Unauthorized: Invalid token."]);
            exit;
        }

        return $user['id'];
    }
}

$authenticatedUserId = authenticateUser($pdo);


function ensureColumnExists($pdo, $table, $column, $definition) {
    try {
        $stmt = $pdo->prepare("SHOW COLUMNS FROM `$table` LIKE ?");
        $stmt->execute([$column]);
        if ($stmt->rowCount() == 0) {
            $pdo->exec("ALTER TABLE `$table` ADD COLUMN `$column` $definition");
        }
    } catch (Exception $e) {
        // Safe to ignore
    }
}

function ensureUniqueIndexExists($pdo, $table, $indexName, $columnsDefinition) {
    try {
        $stmt = $pdo->prepare("SHOW INDEX FROM `$table` WHERE Key_name = ?");
        $stmt->execute([$indexName]);
        if ($stmt->rowCount() == 0) {
            $dupStmt = $pdo->query("SELECT $columnsDefinition, COUNT(*) as cnt FROM `$table` GROUP BY $columnsDefinition HAVING cnt > 1 LIMIT 1");
            if (!$dupStmt || $dupStmt->rowCount() == 0) {
                $pdo->exec("ALTER TABLE `$table` ADD UNIQUE KEY `$indexName` ($columnsDefinition)");
            }
        }
    } catch (Exception $e) {
        // Safe to ignore
    }
}

function ensureAllSyncSchemas($pdo) {
    static $initialized = false;
    if ($initialized) return;
    $initialized = true;

    // 1. Deleted records tombstone table
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS deleted_records (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            collection_name VARCHAR(100) NOT NULL,
            record_id VARCHAR(100) NOT NULL,
            deleted_at BIGINT NOT NULL,
            UNIQUE KEY uq_user_coll_record (user_id, collection_name, record_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'deleted_records', 'collection_name', 'VARCHAR(100) NOT NULL');
    ensureColumnExists($pdo, 'deleted_records', 'record_id', 'VARCHAR(100) NOT NULL');
    ensureColumnExists($pdo, 'deleted_records', 'deleted_at', 'BIGINT NOT NULL');
    ensureColumnExists($pdo, 'deleted_records', 'created_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP');
    ensureUniqueIndexExists($pdo, 'deleted_records', 'uq_user_coll_record', 'user_id, collection_name, record_id');

    // 2. Customers
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS customers (
            id VARCHAR(100) NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            name VARCHAR(255) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'customers', 'phone', "VARCHAR(50) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'customers', 'address', "TEXT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'customers', 'ip_address', "VARCHAR(100) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'customers', 'package_id', "VARCHAR(100) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'customers', 'billing_cycle_date', "INT NOT NULL DEFAULT 1");
    ensureColumnExists($pdo, 'customers', 'status', "VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'");
    ensureColumnExists($pdo, 'customers', 'pppoe_username', "VARCHAR(100) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'customers', 'customer_code', "VARCHAR(100) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'customers', 'joining_date', "VARCHAR(50) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'customers', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'customers', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 3. Packages
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS packages (
            id VARCHAR(100) NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            name VARCHAR(255) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'packages', 'price', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'packages', 'speed', "VARCHAR(100) NULL DEFAULT ''");
    ensureColumnExists($pdo, 'packages', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'packages', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 4. Bills
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS bills (
            id BIGINT NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            customer_id BIGINT NOT NULL,
            month VARCHAR(50) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'bills', 'bill_number', "VARCHAR(100) NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'bills', 'customer_name', "VARCHAR(255) NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'bills', 'customer_code', "VARCHAR(100) NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'bills', 'amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'bills', 'paid_amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'bills', 'due_amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'bills', 'status', "VARCHAR(50) NOT NULL DEFAULT 'UNPAID'");
    ensureColumnExists($pdo, 'bills', 'due_date', "VARCHAR(50) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'bills', 'generated_date', "VARCHAR(50) NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'bills', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'bills', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 5. Payments
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS payments (
            id BIGINT NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            payment_receipt_no VARCHAR(100) NOT NULL,
            bill_id BIGINT NOT NULL DEFAULT 0,
            customer_id BIGINT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'payments', 'customer_name', "VARCHAR(255) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'payments', 'amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'payments', 'payment_date', "VARCHAR(50) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'payments', 'payment_method', "VARCHAR(50) NOT NULL DEFAULT 'Cash'");
    ensureColumnExists($pdo, 'payments', 'notes', "TEXT NULL");
    ensureColumnExists($pdo, 'payments', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'payments', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 6. Expenses
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS expenses (
            id BIGINT NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            title VARCHAR(255) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'expenses', 'amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'expenses', 'category', "VARCHAR(100) NOT NULL DEFAULT 'General'");
    ensureColumnExists($pdo, 'expenses', 'date', "VARCHAR(50) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'expenses', 'payment_method', "VARCHAR(50) NOT NULL DEFAULT 'Cash'");
    ensureColumnExists($pdo, 'expenses', 'note', "TEXT NULL");
    ensureColumnExists($pdo, 'expenses', 'receipt_path', "VARCHAR(255) NULL");
    ensureColumnExists($pdo, 'expenses', 'created_at', "BIGINT NULL");
    ensureColumnExists($pdo, 'expenses', 'updated_at', "BIGINT NULL");
    ensureColumnExists($pdo, 'expenses', 'created_timestamp', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 7. Expense Categories
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS expense_categories (
            id BIGINT NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            name VARCHAR(255) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'expense_categories', 'color', "VARCHAR(50) NOT NULL DEFAULT '#6750A4'");
    ensureColumnExists($pdo, 'expense_categories', 'created_at', "BIGINT NULL");
    ensureColumnExists($pdo, 'expense_categories', 'updated_at', "BIGINT NULL");
    ensureColumnExists($pdo, 'expense_categories', 'created_timestamp', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 8. Business Settings
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS business_settings (
            id INT NOT NULL DEFAULT 1,
            user_id VARCHAR(100) NOT NULL PRIMARY KEY
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'business_settings', 'isp_name', "VARCHAR(255) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'business_settings', 'hotline', "VARCHAR(100) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'business_settings', 'address', "TEXT NULL");
    ensureColumnExists($pdo, 'business_settings', 'currency_symbol', "VARCHAR(20) NOT NULL DEFAULT '৳'");
    ensureColumnExists($pdo, 'business_settings', 'network_status', "VARCHAR(50) NOT NULL DEFAULT 'Operational'");
    ensureColumnExists($pdo, 'business_settings', 'theme_mode', "VARCHAR(50) NOT NULL DEFAULT 'SYSTEM'");
    ensureColumnExists($pdo, 'business_settings', 'logo_uri', "TEXT NULL");
    ensureColumnExists($pdo, 'business_settings', 'email', "VARCHAR(255) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'business_settings', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'business_settings', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 9. Audit Logs
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS audit_logs (
            id BIGINT NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            action VARCHAR(255) NOT NULL,
            timestamp BIGINT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'audit_logs', 'action_type', "VARCHAR(100) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'audit_logs', 'details', "TEXT NULL");
    ensureColumnExists($pdo, 'audit_logs', 'user_email', "VARCHAR(255) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'audit_logs', 'user_role', "VARCHAR(100) NOT NULL DEFAULT 'Admin'");
    ensureColumnExists($pdo, 'audit_logs', 'target_entity', "VARCHAR(100) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'audit_logs', 'target_id', "VARCHAR(100) NOT NULL DEFAULT ''");
    ensureColumnExists($pdo, 'audit_logs', 'previous_state', "TEXT NULL");
    ensureColumnExists($pdo, 'audit_logs', 'new_state', "TEXT NULL");
    ensureColumnExists($pdo, 'audit_logs', 'status', "VARCHAR(50) NOT NULL DEFAULT 'SUCCESS'");
    ensureColumnExists($pdo, 'audit_logs', 'created_timestamp', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 10. Bandwidth Bills
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS bandwidth_bills (
            user_id VARCHAR(100) NOT NULL,
            billing_month VARCHAR(100) NOT NULL,
            PRIMARY KEY (user_id, billing_month)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'bandwidth_bills', 'amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'bandwidth_bills', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'bandwidth_bills', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");

    // 11. Specific Advances
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS specific_advances (
            id BIGINT NOT NULL PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            customer_id BIGINT NOT NULL,
            billing_month VARCHAR(100) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {}
    ensureColumnExists($pdo, 'specific_advances', 'amount', "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    ensureColumnExists($pdo, 'specific_advances', 'is_consumed', "TINYINT(1) NOT NULL DEFAULT 0");
    ensureColumnExists($pdo, 'specific_advances', 'updated_at', "BIGINT NULL DEFAULT NULL");
    ensureColumnExists($pdo, 'specific_advances', 'created_at', "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
}

function fetchDelta($pdo, $userId, $since) {
    $delta = [];

    // Customers
    $stmt = $pdo->prepare("SELECT id, user_id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, updated_at, created_at FROM customers WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['customers'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Packages
    $stmt = $pdo->prepare("SELECT id, user_id, name, price, speed, updated_at, created_at FROM packages WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['packages'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Bills
    $stmt = $pdo->prepare("SELECT id, user_id, customer_id, bill_number, customer_name, customer_code, month, amount, paid_amount, due_amount, status, due_date, generated_date, updated_at, created_at FROM bills WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['bills'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Payments
    $stmt = $pdo->prepare("SELECT id, user_id, payment_receipt_no, bill_id, customer_id, customer_name, amount, payment_date, payment_method, notes, updated_at, created_at FROM payments WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['payments'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Expenses
    $stmt = $pdo->prepare("SELECT id, user_id, title, amount, category, date, payment_method, note, receipt_path, created_at, updated_at FROM expenses WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['expenses'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Expense Categories
    $stmt = $pdo->prepare("SELECT id, user_id, name, color, created_at, updated_at FROM expense_categories WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['expense_categories'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Business Settings
    $stmt = $pdo->prepare("SELECT id, user_id, isp_name, hotline, address, currency_symbol, network_status, theme_mode, logo_uri, email, updated_at, created_at FROM business_settings WHERE user_id = ? AND (updated_at > ? OR ? = 0) LIMIT 1");
    $stmt->execute([$userId, $since, $since]);
    $settings = $stmt->fetch(PDO::FETCH_ASSOC);
    $delta['settings'] = $settings ? $settings : null;

    // Audit Logs
    $stmt = $pdo->prepare("SELECT id, user_id, action, action_type, details, user_email, user_role, target_entity, target_id, previous_state, new_state, status, timestamp FROM audit_logs WHERE user_id = ? AND (timestamp > ? OR ? = 0) ORDER BY timestamp DESC LIMIT 200");
    $stmt->execute([$userId, $since, $since]);
    $delta['audit_logs'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Bandwidth Bills
    $stmt = $pdo->prepare("SELECT user_id, billing_month, amount, updated_at, created_at FROM bandwidth_bills WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['bandwidth_bills'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Specific Advances
    $stmt = $pdo->prepare("SELECT id, user_id, customer_id, billing_month, amount, is_consumed, updated_at, created_at FROM specific_advances WHERE user_id = ? AND (updated_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['specific_advances'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Deleted Records (Tombstones)
    $stmt = $pdo->prepare("SELECT id, user_id, collection_name, record_id, deleted_at FROM deleted_records WHERE user_id = ? AND (deleted_at > ? OR ? = 0)");
    $stmt->execute([$userId, $since, $since]);
    $delta['deleted_records'] = $stmt->fetchAll(PDO::FETCH_ASSOC);

    return $delta;
}

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    try {
        $userId = $authenticatedUserId;
        $since = isset($_GET['since']) ? (int)$_GET['since'] : (isset($_GET['last_sync_timestamp']) ? (int)$_GET['last_sync_timestamp'] : 0);

        if (!$userId) {
            echo json_encode(["status" => false, "message" => "user_id is required"]);
            exit;
        }

        ensureAllSyncSchemas($pdo);

        $now = (int)(microtime(true) * 1000);
        $delta = fetchDelta($pdo, $userId, $since);

        echo json_encode([
            "status" => true,
            "server_timestamp" => $now,
            "data" => $delta
        ]);
        exit;
    } catch (Exception $e) {
        echo json_encode([
            "status" => false,
            "message" => "Sync pull delta error: " . $e->getMessage()
        ]);
        exit;
    }
}

if ($method === 'POST') {
    try {
        $raw = file_get_contents("php://input");
        $payload = json_decode($raw, true);

        $userId = $authenticatedUserId;
        $since = isset($payload['last_sync_timestamp']) ? (int)$payload['last_sync_timestamp'] : 0;

        if (!$userId) {
            echo json_encode(["status" => false, "message" => "user_id is required"]);
            exit;
        }

        ensureAllSyncSchemas($pdo);

        $now = (int)(microtime(true) * 1000);

        $syncedIds = [
            "customers" => [],
            "packages" => [],
            "bills" => [],
            "payments" => [],
            "expenses" => [],
            "expense_categories" => [],
            "settings" => null,
            "audit_logs" => [],
            "bandwidth_bills" => [],
            "specific_advances" => [],
            "deleted_records" => []
        ];

        // 1. Process Customers
        if (!empty($payload['customers']) && is_array($payload['customers'])) {
            $stmt = $pdo->prepare("INSERT INTO customers (id, user_id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone), address = VALUES(address), ip_address = VALUES(ip_address), package_id = VALUES(package_id), billing_cycle_date = VALUES(billing_cycle_date), status = VALUES(status), pppoe_username = VALUES(pppoe_username), customer_code = VALUES(customer_code), joining_date = VALUES(joining_date), updated_at = VALUES(updated_at)");
            foreach ($payload['customers'] as $c) {
                if (!empty($c['id']) && !empty($c['name'])) {
                    $upAt = isset($c['updated_at']) ? (int)$c['updated_at'] : $now;
                    $stmt->execute([
                        (string)$c['id'],
                        $userId,
                        (string)$c['name'],
                        $c['phone'] ?? '',
                        $c['address'] ?? '',
                        $c['ip_address'] ?? '',
                        $c['package_id'] ?? '',
                        isset($c['billing_cycle_date']) ? (int)$c['billing_cycle_date'] : 1,
                        $c['status'] ?? 'ACTIVE',
                        $c['pppoe_username'] ?? '',
                        $c['customer_code'] ?? '',
                        $c['joining_date'] ?? '',
                        $upAt
                    ]);
                    $syncedIds['customers'][] = (string)$c['id'];
                }
            }
        }

        // 2. Process Packages
        if (!empty($payload['packages']) && is_array($payload['packages'])) {
            $stmt = $pdo->prepare("INSERT INTO packages (id, user_id, name, price, speed, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price), speed = VALUES(speed), updated_at = VALUES(updated_at)");
            foreach ($payload['packages'] as $p) {
                if (!empty($p['id']) && !empty($p['name'])) {
                    $upAt = isset($p['updated_at']) ? (int)$p['updated_at'] : $now;
                    $stmt->execute([
                        (string)$p['id'],
                        $userId,
                        (string)$p['name'],
                        (float)$p['price'],
                        $p['speed'] ?? '',
                        $upAt
                    ]);
                    $syncedIds['packages'][] = (string)$p['id'];
                }
            }
        }

        // 3. Process Bills
        if (!empty($payload['bills']) && is_array($payload['bills'])) {
            $stmt = $pdo->prepare("INSERT INTO bills (id, user_id, customer_id, bill_number, customer_name, customer_code, month, amount, paid_amount, due_amount, status, due_date, generated_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE customer_id = VALUES(customer_id), bill_number = VALUES(bill_number), customer_name = VALUES(customer_name), customer_code = VALUES(customer_code), month = VALUES(month), amount = VALUES(amount), paid_amount = VALUES(paid_amount), due_amount = VALUES(due_amount), status = VALUES(status), due_date = VALUES(due_date), generated_date = VALUES(generated_date), updated_at = VALUES(updated_at)");
            foreach ($payload['bills'] as $b) {
                if (!empty($b['id'])) {
                    $upAt = isset($b['updated_at']) ? (int)$b['updated_at'] : $now;
                    $stmt->execute([
                        (int)$b['id'],
                        $userId,
                        (int)($b['customer_id'] ?? 0),
                        $b['bill_number'] ?? null,
                        $b['customer_name'] ?? null,
                        $b['customer_code'] ?? null,
                        $b['month'] ?? '',
                        (float)($b['amount'] ?? 0),
                        (float)($b['paid_amount'] ?? 0),
                        (float)($b['due_amount'] ?? 0),
                        $b['status'] ?? 'UNPAID',
                        $b['due_date'] ?? '',
                        $b['generated_date'] ?? null,
                        $upAt
                    ]);
                    $syncedIds['bills'][] = (int)$b['id'];
                }
            }
        }

        // 4. Process Payments
        if (!empty($payload['payments']) && is_array($payload['payments'])) {
            $stmt = $pdo->prepare("INSERT INTO payments (id, user_id, payment_receipt_no, bill_id, customer_id, customer_name, amount, payment_date, payment_method, notes, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE payment_receipt_no = VALUES(payment_receipt_no), bill_id = VALUES(bill_id), customer_id = VALUES(customer_id), customer_name = VALUES(customer_name), amount = VALUES(amount), payment_date = VALUES(payment_date), payment_method = VALUES(payment_method), notes = VALUES(notes), updated_at = VALUES(updated_at)");
            foreach ($payload['payments'] as $pm) {
                if (!empty($pm['id'])) {
                    $upAt = isset($pm['updated_at']) ? (int)$pm['updated_at'] : $now;
                    $stmt->execute([
                        (int)$pm['id'],
                        $userId,
                        $pm['payment_receipt_no'] ?? ('REC-' . $pm['id']),
                        (int)($pm['bill_id'] ?? 0),
                        (int)($pm['customer_id'] ?? 0),
                        $pm['customer_name'] ?? '',
                        (float)($pm['amount'] ?? 0),
                        $pm['payment_date'] ?? '',
                        $pm['payment_method'] ?? 'Cash',
                        $pm['notes'] ?? null,
                        $upAt
                    ]);
                    $syncedIds['payments'][] = (int)$pm['id'];
                }
            }
        }

        // 5. Process Expenses
        if (!empty($payload['expenses']) && is_array($payload['expenses'])) {
            $stmt = $pdo->prepare("INSERT INTO expenses (id, user_id, title, amount, category, date, payment_method, note, receipt_path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE title = VALUES(title), amount = VALUES(amount), category = VALUES(category), date = VALUES(date), payment_method = VALUES(payment_method), note = VALUES(note), receipt_path = VALUES(receipt_path), updated_at = VALUES(updated_at)");
            foreach ($payload['expenses'] as $e) {
                if (!empty($e['id'])) {
                    $upAt = isset($e['updated_at']) ? (int)$e['updated_at'] : $now;
                    $crAt = isset($e['created_at']) ? (int)$e['created_at'] : $now;
                    $stmt->execute([
                        (int)$e['id'],
                        $userId,
                        $e['title'] ?? '',
                        (float)($e['amount'] ?? 0),
                        $e['category'] ?? 'General',
                        $e['date'] ?? '',
                        $e['payment_method'] ?? 'Cash',
                        $e['note'] ?? null,
                        $e['receipt_path'] ?? null,
                        $crAt,
                        $upAt
                    ]);
                    $syncedIds['expenses'][] = (int)$e['id'];
                }
            }
        }

        // 6. Process Expense Categories
        if (!empty($payload['expense_categories']) && is_array($payload['expense_categories'])) {
            $stmt = $pdo->prepare("INSERT INTO expense_categories (id, user_id, name, color, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), color = VALUES(color), updated_at = VALUES(updated_at)");
            foreach ($payload['expense_categories'] as $ec) {
                if (!empty($ec['id'])) {
                    $upAt = isset($ec['updated_at']) ? (int)$ec['updated_at'] : $now;
                    $crAt = isset($ec['created_at']) ? (int)$ec['created_at'] : $now;
                    $stmt->execute([
                        (int)$ec['id'],
                        $userId,
                        $ec['name'] ?? '',
                        $ec['color'] ?? '#6750A4',
                        $crAt,
                        $upAt
                    ]);
                    $syncedIds['expense_categories'][] = (int)$ec['id'];
                }
            }
        }

        // 7. Process Business Settings
        if (!empty($payload['settings']) && is_array($payload['settings'])) {
            $s = $payload['settings'];
            $upAt = isset($s['updated_at']) ? (int)$s['updated_at'] : $now;
            $stmt = $pdo->prepare("INSERT INTO business_settings (id, user_id, isp_name, hotline, address, currency_symbol, network_status, theme_mode, logo_uri, email, updated_at) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE isp_name = VALUES(isp_name), hotline = VALUES(hotline), address = VALUES(address), currency_symbol = VALUES(currency_symbol), network_status = VALUES(network_status), theme_mode = VALUES(theme_mode), logo_uri = VALUES(logo_uri), email = VALUES(email), updated_at = VALUES(updated_at)");
            $stmt->execute([
                $userId,
                $s['isp_name'] ?? '',
                $s['hotline'] ?? '',
                $s['address'] ?? null,
                $s['currency_symbol'] ?? '৳',
                $s['network_status'] ?? 'Operational',
                $s['theme_mode'] ?? 'SYSTEM',
                $s['logo_uri'] ?? null,
                $s['email'] ?? '',
                $upAt
            ]);
            $syncedIds['settings'] = 1;
        }

        // 8. Process Audit Logs
        if (!empty($payload['audit_logs']) && is_array($payload['audit_logs'])) {
            $stmt = $pdo->prepare("INSERT INTO audit_logs (id, user_id, action, action_type, details, user_email, user_role, target_entity, target_id, previous_state, new_state, status, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE action = VALUES(action), details = VALUES(details), timestamp = VALUES(timestamp)");
            foreach ($payload['audit_logs'] as $al) {
                if (!empty($al['id'])) {
                    $stmt->execute([
                        (int)$al['id'],
                        $userId,
                        $al['action'] ?? '',
                        $al['action_type'] ?? '',
                        $al['details'] ?? null,
                        $al['user_email'] ?? '',
                        $al['user_role'] ?? 'Admin',
                        $al['target_entity'] ?? '',
                        $al['target_id'] ?? '',
                        $al['previous_state'] ?? null,
                        $al['new_state'] ?? null,
                        $al['status'] ?? 'SUCCESS',
                        (int)($al['timestamp'] ?? $now)
                    ]);
                    $syncedIds['audit_logs'][] = (int)$al['id'];
                }
            }
        }

        // 9. Process Bandwidth Bills
        if (!empty($payload['bandwidth_bills']) && is_array($payload['bandwidth_bills'])) {
            $stmt = $pdo->prepare("INSERT INTO bandwidth_bills (user_id, billing_month, amount, updated_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount), updated_at = VALUES(updated_at)");
            foreach ($payload['bandwidth_bills'] as $bb) {
                if (!empty($bb['billing_month'])) {
                    $upAt = isset($bb['updated_at']) ? (int)$bb['updated_at'] : $now;
                    $stmt->execute([
                        $userId,
                        (string)$bb['billing_month'],
                        (float)($bb['amount'] ?? 0),
                        $upAt
                    ]);
                    $syncedIds['bandwidth_bills'][] = (string)$bb['billing_month'];
                }
            }
        }

        // 10. Process Specific Advances
        if (!empty($payload['specific_advances']) && is_array($payload['specific_advances'])) {
            $stmt = $pdo->prepare("INSERT INTO specific_advances (id, user_id, customer_id, billing_month, amount, is_consumed, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE customer_id = VALUES(customer_id), billing_month = VALUES(billing_month), amount = VALUES(amount), is_consumed = VALUES(is_consumed), updated_at = VALUES(updated_at)");
            foreach ($payload['specific_advances'] as $sa) {
                if (!empty($sa['id'])) {
                    $upAt = isset($sa['updated_at']) ? (int)$sa['updated_at'] : $now;
                    $stmt->execute([
                        (int)$sa['id'],
                        $userId,
                        (int)($sa['customer_id'] ?? 0),
                        (string)($sa['billing_month'] ?? ''),
                        (float)($sa['amount'] ?? 0),
                        !empty($sa['is_consumed']) ? 1 : 0,
                        $upAt
                    ]);
                    $syncedIds['specific_advances'][] = (int)$sa['id'];
                }
            }
        }

        // 11. Process Deleted Records (Tombstones)
        if (!empty($payload['deleted_records']) && is_array($payload['deleted_records'])) {
            $tombstoneStmt = $pdo->prepare("INSERT INTO deleted_records (user_id, collection_name, record_id, deleted_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE deleted_at = VALUES(deleted_at)");

            foreach ($payload['deleted_records'] as $del) {
                $coll = $del['collection_name'] ?? '';
                $recId = $del['record_id'] ?? '';
                $delAt = isset($del['deleted_at']) ? (int)$del['deleted_at'] : $now;

                if ($coll !== '' && $recId !== '') {
                    // Delete from domain table
                    switch ($coll) {
                        case 'customers':
                            $dStmt = $pdo->prepare("DELETE FROM customers WHERE id = ? AND user_id = ?");
                            $dStmt->execute([$recId, $userId]);
                            break;
                        case 'packages':
                            $dStmt = $pdo->prepare("DELETE FROM packages WHERE id = ? AND user_id = ?");
                            $dStmt->execute([$recId, $userId]);
                            break;
                        case 'bills':
                            $dStmt = $pdo->prepare("DELETE FROM bills WHERE id = ? AND user_id = ?");
                            $dStmt->execute([(int)$recId, $userId]);
                            break;
                        case 'payments':
                            $dStmt = $pdo->prepare("DELETE FROM payments WHERE id = ? AND user_id = ?");
                            $dStmt->execute([(int)$recId, $userId]);
                            break;
                        case 'expenses':
                            $dStmt = $pdo->prepare("DELETE FROM expenses WHERE id = ? AND user_id = ?");
                            $dStmt->execute([(int)$recId, $userId]);
                            break;
                        case 'expense_categories':
                            $dStmt = $pdo->prepare("DELETE FROM expense_categories WHERE id = ? AND user_id = ?");
                            $dStmt->execute([(int)$recId, $userId]);
                            break;
                        case 'bandwidth_bills':
                            $dStmt = $pdo->prepare("DELETE FROM bandwidth_bills WHERE billing_month = ? AND user_id = ?");
                            $dStmt->execute([$recId, $userId]);
                            break;
                        case 'specific_advances':
                            $dStmt = $pdo->prepare("DELETE FROM specific_advances WHERE id = ? AND user_id = ?");
                            $dStmt->execute([(int)$recId, $userId]);
                            break;
                    }

                    // Record tombstone
                    $tombstoneStmt->execute([$userId, $coll, (string)$recId, $delAt]);
                    $syncedIds['deleted_records'][] = $coll . ':' . $recId;
                }
            }
        }

        // Now fetch delta for the client
        $delta = fetchDelta($pdo, $userId, $since);

        echo json_encode([
            "status" => true,
            "message" => "Sync completed successfully",
            "server_timestamp" => $now,
            "synced_ids" => $syncedIds,
            "data" => $delta
        ]);
        exit;
    } catch (Exception $e) {
        echo json_encode([
            "status" => false,
            "message" => "Sync push data error: " . $e->getMessage()
        ]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
