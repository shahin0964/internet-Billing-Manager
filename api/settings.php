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

function ensureBusinessSettingsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS business_settings (
            id INT NOT NULL DEFAULT 1,
            user_id VARCHAR(100) NOT NULL,
            isp_name VARCHAR(255) NOT NULL DEFAULT '',
            hotline VARCHAR(100) NOT NULL DEFAULT '',
            address TEXT NULL,
            currency_symbol VARCHAR(20) NOT NULL DEFAULT '৳',
            network_status VARCHAR(50) NOT NULL DEFAULT 'Operational',
            theme_mode VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
            logo_uri TEXT NULL,
            email VARCHAR(255) NOT NULL DEFAULT '',
            updated_at BIGINT NULL DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id),
            INDEX idx_user_id (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM business_settings");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'id' => "INT NOT NULL DEFAULT 1",
            'isp_name' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'hotline' => "VARCHAR(100) NOT NULL DEFAULT ''",
            'address' => "TEXT NULL",
            'currency_symbol' => "VARCHAR(20) NOT NULL DEFAULT '৳'",
            'network_status' => "VARCHAR(50) NOT NULL DEFAULT 'Operational'",
            'theme_mode' => "VARCHAR(50) NOT NULL DEFAULT 'SYSTEM'",
            'logo_uri' => "TEXT NULL",
            'email' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'updated_at' => "BIGINT NULL DEFAULT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE business_settings ADD COLUMN $col $definition");
            }
        }
    } catch (Exception $e) {
        // Continue safely
    }
}

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $userId = $_GET['user_id'] ?? null;
    if (!$userId) {
        echo json_encode(["status" => false, "message" => "user_id is required"]);
        exit;
    }

    ensureBusinessSettingsSchema($pdo);

    $stmt = $pdo->prepare("SELECT * FROM business_settings WHERE user_id = ? LIMIT 1");
    $stmt->execute([$userId]);
    $settings = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($settings) {
        echo json_encode(["status" => true, "data" => $settings]);
    } else {
        echo json_encode(["status" => true, "data" => null]);
    }
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $userId = $data['user_id'] ?? null;
    if (!$userId || trim($userId) === '') {
        echo json_encode(["status" => false, "message" => "user_id is required"]);
        exit;
    }

    ensureBusinessSettingsSchema($pdo);

    $id = isset($data['id']) ? intval($data['id']) : 1;
    $ispName = $data['isp_name'] ?? ($data['ispName'] ?? '');
    $hotline = $data['hotline'] ?? '';
    $address = $data['address'] ?? '';
    $currencySymbol = $data['currency_symbol'] ?? ($data['currencySymbol'] ?? '৳');
    $networkStatus = $data['network_status'] ?? ($data['networkStatus'] ?? 'Operational');
    $themeMode = $data['theme_mode'] ?? ($data['themeMode'] ?? 'SYSTEM');
    $logoUri = $data['logo_uri'] ?? ($data['logoUri'] ?? null);
    $email = $data['email'] ?? '';
    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? null);

    $checkStmt = $pdo->prepare("SELECT user_id FROM business_settings WHERE user_id = ?");
    $checkStmt->execute([$userId]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $pdo->prepare("UPDATE business_settings SET 
            id = ?,
            isp_name = ?, 
            hotline = ?, 
            address = ?, 
            currency_symbol = ?, 
            network_status = ?, 
            theme_mode = ?, 
            logo_uri = ?, 
            email = ?, 
            updated_at = ? 
            WHERE user_id = ?");
        $updateStmt->execute([
            $id,
            $ispName,
            $hotline,
            $address,
            $currencySymbol,
            $networkStatus,
            $themeMode,
            $logoUri,
            $email,
            $updatedAt,
            $userId
        ]);

        echo json_encode(["status" => true, "message" => "Settings updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO business_settings (
            id, user_id, isp_name, hotline, address,
            currency_symbol, network_status, theme_mode, logo_uri,
            email, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $insertStmt->execute([
            $id,
            $userId,
            $ispName,
            $hotline,
            $address,
            $currencySymbol,
            $networkStatus,
            $themeMode,
            $logoUri,
            $email,
            $updatedAt
        ]);

        echo json_encode(["status" => true, "message" => "Settings created successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
