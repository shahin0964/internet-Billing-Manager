<?php
// Central Database Router & Multi-Tenant Database-per-User Manager

$dbHost = getenv('DB_HOST') ?: '127.0.0.1';
$dbUser = getenv('DB_USER') ?: 'root';
$dbPass = getenv('DB_PASS') ?: '';
$dbSystemName = getenv('DB_SYSTEM_NAME') ?: (getenv('DB_NAME') ?: 'isp_system');

$pdoOptions = [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
];

function getSystemPdo() {
    global $dbHost, $dbUser, $dbPass, $dbSystemName, $pdoOptions;
    static $systemPdo = null;
    if ($systemPdo !== null) {
        return $systemPdo;
    }

    try {
        // Connect to server (without db specified initially to ensure system db exists)
        $pdo = new PDO("mysql:host=$dbHost;charset=utf8mb4", $dbUser, $dbPass, $pdoOptions);
        $pdo->exec("CREATE DATABASE IF NOT EXISTS `$dbSystemName` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        $pdo->exec("USE `$dbSystemName`");
        $systemPdo = $pdo;
    } catch (Exception $e) {
        // Fallback direct connection
        $systemPdo = new PDO("mysql:host=$dbHost;dbname=$dbSystemName;charset=utf8mb4", $dbUser, $dbPass, $pdoOptions);
    }

    ensureSystemUsersSchema($systemPdo);
    return $systemPdo;
}

function ensureSystemUsersSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS users (
            id VARCHAR(100) NOT NULL,
            name VARCHAR(255) NOT NULL DEFAULT '',
            email VARCHAR(255) NOT NULL,
            password_hash VARCHAR(255) NOT NULL,
            phone VARCHAR(50) NULL DEFAULT '',
            role VARCHAR(50) NOT NULL DEFAULT 'User',
            status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
            api_token VARCHAR(128) NULL UNIQUE,
            db_name VARCHAR(128) NULL UNIQUE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            UNIQUE KEY uk_email (email),
            INDEX idx_email (email),
            INDEX idx_api_token (api_token)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM users");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'name' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'email' => "VARCHAR(255) NOT NULL",
            'password_hash' => "VARCHAR(255) NOT NULL",
            'phone' => "VARCHAR(50) NULL DEFAULT ''",
            'role' => "VARCHAR(50) NOT NULL DEFAULT 'User'",
            'status' => "VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'",
            'api_token' => "VARCHAR(128) NULL UNIQUE",
            'db_name' => "VARCHAR(128) NULL UNIQUE"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE users ADD COLUMN `$col` $definition");
            }
        }
    } catch (Exception $e) {
        // Continue safely
    }
}

function generateSafeAccountDbName($userId) {
    // Sanitize user ID to create a safe MySQL identifier: isp_acc_<id>
    $clean = preg_replace('/[^a-zA-Z0-9_]/', '', str_replace('-', '_', $userId));
    if (empty($clean)) {
        $clean = bin2hex(random_bytes(6));
    }
    return 'isp_acc_' . $clean;
}

function getAuthenticatedUser($systemPdo) {
    $token = null;
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    if (isset($headers['Authorization'])) {
        if (preg_match('/Bearer\s(\S+)/', $headers['Authorization'], $matches)) {
            $token = $matches[1];
        }
    } elseif (isset($_SERVER['HTTP_AUTHORIZATION'])) {
        if (preg_match('/Bearer\s(\S+)/', $_SERVER['HTTP_AUTHORIZATION'], $matches)) {
            $token = $matches[1];
        }
    } elseif (isset($_REQUEST['api_token'])) {
        $token = trim($_REQUEST['api_token']);
    }

    if (empty($token)) {
        // For backwards compatibility during transition or login/register
        $userId = $_REQUEST['user_id'] ?? null;
        if ($userId) {
            $stmt = $systemPdo->prepare("SELECT id, name, email, role, status, api_token, db_name FROM users WHERE id = ? LIMIT 1");
            $stmt->execute([$userId]);
            $user = $stmt->fetch(PDO::FETCH_ASSOC);
            if ($user) return $user;
        }

        http_response_code(401);
        echo json_encode(["status" => false, "message" => "Unauthorized: Missing authentication token."]);
        exit;
    }

    $stmt = $systemPdo->prepare("SELECT id, name, email, role, status, api_token, db_name FROM users WHERE api_token = ? LIMIT 1");
    $stmt->execute([$token]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user) {
        http_response_code(401);
        echo json_encode(["status" => false, "message" => "Unauthorized: Invalid or expired token."]);
        exit;
    }

    return $user;
}

function provisionUserDatabase($systemPdo, $userId) {
    global $dbHost, $dbUser, $dbPass, $pdoOptions;

    // Check if user already has db_name
    $stmt = $systemPdo->prepare("SELECT db_name FROM users WHERE id = ? LIMIT 1");
    $stmt->execute([$userId]);
    $existingDbName = $stmt->fetchColumn();

    $dbName = $existingDbName;
    if (empty($dbName)) {
        $dbName = generateSafeAccountDbName($userId);
        $updateStmt = $systemPdo->prepare("UPDATE users SET db_name = ? WHERE id = ?");
        $updateStmt->execute([$dbName, $userId]);
    }

    // Strictly validate database name identifier to prevent SQL injection
    if (!preg_match('/^[a-zA-Z0-9_]+$/', $dbName)) {
        throw new RuntimeException("Invalid account database identifier.");
    }

    // Create physical database
    $systemPdo->exec("CREATE DATABASE IF NOT EXISTS `$dbName` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

    // Connect to newly created user database and initialize 12 tables
    $accountPdo = new PDO("mysql:host=$dbHost;dbname=$dbName;charset=utf8mb4", $dbUser, $dbPass, $pdoOptions);
    ensureAccountDatabaseTables($accountPdo);

    return [
        'db_name' => $dbName,
        'pdo' => $accountPdo
    ];
}

function getUserDatabaseName($systemPdo, $userId) {
    $stmt = $systemPdo->prepare("SELECT db_name FROM users WHERE id = ? LIMIT 1");
    $stmt->execute([$userId]);
    $dbName = $stmt->fetchColumn();
    if (empty($dbName)) {
        $provision = provisionUserDatabase($systemPdo, $userId);
        return $provision['db_name'];
    }
    return $dbName;
}

function getUserAccountPdo($systemPdo, $userId) {
    return getAccountPdo($systemPdo, $userId);
}

function getAccountPdo($systemPdo, $userId) {
    global $dbHost, $dbUser, $dbPass, $pdoOptions;
    static $accountCache = [];

    if (isset($accountCache[$userId])) {
        return $accountCache[$userId];
    }

    $stmt = $systemPdo->prepare("SELECT db_name FROM users WHERE id = ? LIMIT 1");
    $stmt->execute([$userId]);
    $dbName = $stmt->fetchColumn();

    if (empty($dbName)) {
        $provision = provisionUserDatabase($systemPdo, $userId);
        $accountCache[$userId] = $provision['pdo'];
        return $provision['pdo'];
    }

    if (!preg_match('/^[a-zA-Z0-9_]+$/', $dbName)) {
        throw new RuntimeException("Invalid account database identifier.");
    }

    try {
        $accountPdo = new PDO("mysql:host=$dbHost;dbname=$dbName;charset=utf8mb4", $dbUser, $dbPass, $pdoOptions);
        ensureAccountDatabaseTables($accountPdo);
        $accountCache[$userId] = $accountPdo;
        return $accountPdo;
    } catch (Exception $e) {
        // Re-provision if db missing
        $provision = provisionUserDatabase($systemPdo, $userId);
        $accountCache[$userId] = $provision['pdo'];
        return $provision['pdo'];
    }
}

function ensureAccountDatabaseTables($pdo) {
    static $initializedTables = [];
    $pdoHash = spl_object_hash($pdo);
    if (isset($initializedTables[$pdoHash])) return;
    $initializedTables[$pdoHash] = true;

    // 1. Audit Logs
    $pdo->exec("CREATE TABLE IF NOT EXISTS audit_logs (
        id BIGINT NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        action VARCHAR(255) NOT NULL,
        action_type VARCHAR(100) NOT NULL DEFAULT '',
        details TEXT NULL,
        user_email VARCHAR(255) NOT NULL DEFAULT '',
        user_role VARCHAR(100) NOT NULL DEFAULT 'Admin',
        target_entity VARCHAR(100) NOT NULL DEFAULT '',
        target_id VARCHAR(100) NOT NULL DEFAULT '',
        previous_state TEXT NULL,
        new_state TEXT NULL,
        status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
        timestamp BIGINT NOT NULL,
        created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_action_type (action_type),
        INDEX idx_timestamp (timestamp)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 2. Bandwidth Bills
    $pdo->exec("CREATE TABLE IF NOT EXISTS bandwidth_bills (
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        billing_month VARCHAR(100) NOT NULL,
        amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (billing_month)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 3. Bills (with unique customer + canonical billing month constraint)
    $pdo->exec("CREATE TABLE IF NOT EXISTS bills (
        id BIGINT NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        customer_id BIGINT NOT NULL,
        bill_number VARCHAR(100) NULL DEFAULT NULL,
        customer_name VARCHAR(255) NULL DEFAULT NULL,
        customer_code VARCHAR(100) NULL DEFAULT NULL,
        month VARCHAR(50) NOT NULL,
        bill_month VARCHAR(50) NULL DEFAULT NULL,
        amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        due_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        status VARCHAR(50) NOT NULL DEFAULT 'UNPAID',
        due_date VARCHAR(50) NOT NULL DEFAULT '',
        generated_date VARCHAR(50) NULL DEFAULT NULL,
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_customer_id (customer_id),
        INDEX idx_month (month)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Add unique constraint on customer_id + month if safe
    try {
        $idxStmt = $pdo->query("SHOW INDEX FROM bills WHERE Key_name = 'uq_customer_billing_month'");
        if ($idxStmt->rowCount() == 0) {
            $dupCheck = $pdo->query("SELECT customer_id, month, COUNT(*) as cnt FROM bills GROUP BY customer_id, month HAVING cnt > 1 LIMIT 1");
            if (!$dupCheck || $dupCheck->rowCount() == 0) {
                $pdo->exec("ALTER TABLE bills ADD UNIQUE KEY uq_customer_billing_month (customer_id, month)");
            }
        }
    } catch (Exception $e) {}

    // 4. Business Settings
    $pdo->exec("CREATE TABLE IF NOT EXISTS business_settings (
        id INT NOT NULL PRIMARY KEY DEFAULT 1,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        isp_name VARCHAR(255) NOT NULL DEFAULT '',
        hotline VARCHAR(100) NOT NULL DEFAULT '',
        address TEXT NULL,
        currency_symbol VARCHAR(20) NOT NULL DEFAULT '৳',
        network_status VARCHAR(50) NOT NULL DEFAULT 'Operational',
        theme_mode VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
        logo_uri TEXT NULL,
        email VARCHAR(255) NOT NULL DEFAULT '',
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // Insert default business settings row if empty
    try {
        $settCount = $pdo->query("SELECT COUNT(*) FROM business_settings")->fetchColumn();
        if ($settCount == 0) {
            $pdo->exec("INSERT IGNORE INTO business_settings (id, isp_name, currency_symbol, network_status, theme_mode) VALUES (1, 'My ISP', '৳', 'Operational', 'SYSTEM')");
        }
    } catch (Exception $e) {}

    // 5. Cloud Backups
    $pdo->exec("CREATE TABLE IF NOT EXISTS cloud_backups (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        backup_name VARCHAR(255) NOT NULL DEFAULT '',
        backup_data LONGTEXT NOT NULL,
        backup_size INT NOT NULL DEFAULT 0,
        version INT NOT NULL DEFAULT 1,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at BIGINT NULL DEFAULT NULL,
        INDEX idx_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 6. Customers
    $pdo->exec("CREATE TABLE IF NOT EXISTS customers (
        id VARCHAR(100) NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        name VARCHAR(255) NOT NULL,
        phone VARCHAR(50) NULL DEFAULT '',
        address TEXT NULL DEFAULT NULL,
        ip_address VARCHAR(100) NULL DEFAULT '',
        package_id VARCHAR(100) NULL DEFAULT '',
        billing_cycle_date INT NOT NULL DEFAULT 1,
        status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
        pppoe_username VARCHAR(100) NULL DEFAULT '',
        customer_code VARCHAR(100) NULL DEFAULT '',
        joining_date VARCHAR(50) NULL DEFAULT '',
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_customer_code (customer_code),
        INDEX idx_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 7. Expenses
    $pdo->exec("CREATE TABLE IF NOT EXISTS expenses (
        id BIGINT NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        title VARCHAR(255) NOT NULL,
        amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        category VARCHAR(100) NOT NULL DEFAULT 'General',
        date VARCHAR(50) NOT NULL DEFAULT '',
        payment_method VARCHAR(50) NOT NULL DEFAULT 'Cash',
        note TEXT NULL,
        receipt_path VARCHAR(255) NULL,
        created_at BIGINT NULL,
        updated_at BIGINT NULL,
        created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_category (category),
        INDEX idx_date (date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 9. Expense Categories
    $pdo->exec("CREATE TABLE IF NOT EXISTS expense_categories (
        id BIGINT NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        name VARCHAR(150) NOT NULL,
        color VARCHAR(50) NOT NULL DEFAULT '#6750A4',
        created_at BIGINT NULL,
        updated_at BIGINT NULL,
        created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 10. Packages
    $pdo->exec("CREATE TABLE IF NOT EXISTS packages (
        id VARCHAR(100) NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        name VARCHAR(255) NOT NULL,
        price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        speed VARCHAR(100) NULL DEFAULT '',
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 11. Payments
    $pdo->exec("CREATE TABLE IF NOT EXISTS payments (
        id BIGINT NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        payment_receipt_no VARCHAR(100) NOT NULL,
        bill_id BIGINT NOT NULL DEFAULT 0,
        customer_id BIGINT NOT NULL,
        customer_name VARCHAR(255) NOT NULL DEFAULT '',
        amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        payment_date VARCHAR(50) NOT NULL DEFAULT '',
        payment_method VARCHAR(50) NOT NULL DEFAULT 'Cash',
        notes TEXT NULL,
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_customer_id (customer_id),
        INDEX idx_bill_id (bill_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    // 12. Specific Advances
    $pdo->exec("CREATE TABLE IF NOT EXISTS specific_advances (
        id BIGINT NOT NULL PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL DEFAULT '',
        customer_id BIGINT NOT NULL,
        billing_month VARCHAR(100) NOT NULL,
        amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
        is_consumed TINYINT(1) NOT NULL DEFAULT 0,
        updated_at BIGINT NULL DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_customer_id (customer_id),
        INDEX idx_billing_month (billing_month)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function normalizeCanonicalMonth($rawMonth) {
    if (empty($rawMonth)) return '';
    $trimmed = trim((string)$rawMonth);
    
    // Bengali digits map
    $banglaDigits = ['০'=>'0', '১'=>'1', '২'=>'2', '৩'=>'3', '৪'=>'4', '৫'=>'5', '৬'=>'6', '৭'=>'7', '৮'=>'8', '৯'=>'9'];
    $converted = strtr($trimmed, $banglaDigits);
    $lower = strtolower($converted);

    // Direct match for YYYY-MM
    if (preg_match('/^(\d{4})[-_\/.](\d{1,2})$/', $lower, $m)) {
        return sprintf('%04d-%02d', (int)$m[1], (int)$m[2]);
    }

    // Match for MM-YYYY
    if (preg_match('/^(\d{1,2})[-_\/.](\d{4})$/', $lower, $m)) {
        return sprintf('%04d-%02d', (int)$m[2], (int)$m[1]);
    }

    $monthMap = [
        'january' => '01', 'jan' => '01', 'জানুয়ারি' => '01', 'জানুয়ারী' => '01',
        'february' => '02', 'feb' => '02', 'ফেব্রুয়ারি' => '02', 'ফেব্রুয়ারী' => '02',
        'march' => '03', 'mar' => '03', 'মার্চ' => '03',
        'april' => '04', 'apr' => '04', 'এপ্রিল' => '04',
        'may' => '05', 'মে' => '05',
        'june' => '06', 'jun' => '06', 'জুন' => '06',
        'july' => '07', 'jul' => '07', 'জুলাই' => '07',
        'august' => '08', 'aug' => '08', 'আগস্ট' => '08', 'আগষ্ট' => '08',
        'september' => '09', 'sep' => '09', 'sept' => '09', 'সেপ্টেম্বর' => '09',
        'october' => '10', 'oct' => '10', 'অক্টোবর' => '10',
        'november' => '11', 'nov' => '11', 'নভেম্বর' => '11',
        'december' => '12', 'dec' => '12', 'ডিসেম্বর' => '12'
    ];

    $extractedMonth = null;
    foreach ($monthMap as $name => $num) {
        if (stripos($lower, $name) !== false) {
            $extractedMonth = $num;
            break;
        }
    }

    // Extract 4-digit year
    if (preg_match('/(20\d{2})/', $lower, $ym)) {
        $extractedYear = $ym[1];
        if ($extractedMonth) {
            return $extractedYear . '-' . $extractedMonth;
        }
    }

    return $trimmed;
}

// Global reference for legacy files that require 'db.php' directly
$pdo = getSystemPdo();
