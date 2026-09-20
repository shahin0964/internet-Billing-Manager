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

function ensureSpecificAdvancesSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS specific_advances (
            id BIGINT NOT NULL,
            user_id VARCHAR(100) NOT NULL,
            customer_id BIGINT NOT NULL,
            billing_month VARCHAR(100) NOT NULL,
            amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
            is_consumed TINYINT(1) NOT NULL DEFAULT 0,
            updated_at BIGINT NULL DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            INDEX idx_user_id (user_id),
            INDEX idx_customer_id (customer_id),
            INDEX idx_billing_month (billing_month)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM specific_advances");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'customer_id' => "BIGINT NOT NULL DEFAULT 0",
            'billing_month' => "VARCHAR(100) NOT NULL DEFAULT ''",
            'amount' => "DECIMAL(10,2) NOT NULL DEFAULT 0.00",
            'is_consumed' => "TINYINT(1) NOT NULL DEFAULT 0",
            'updated_at' => "BIGINT NULL DEFAULT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE specific_advances ADD COLUMN $col $definition");
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

    ensureSpecificAdvancesSchema($pdo);

    $customerId = $_GET['customer_id'] ?? null;
    $billingMonth = $_GET['billing_month'] ?? null;

    if ($customerId && $billingMonth) {
        $stmt = $pdo->prepare("SELECT * FROM specific_advances WHERE user_id = ? AND customer_id = ? AND billing_month = ?");
        $stmt->execute([$userId, $customerId, $billingMonth]);
    } elseif ($customerId) {
        $stmt = $pdo->prepare("SELECT * FROM specific_advances WHERE user_id = ? AND customer_id = ?");
        $stmt->execute([$userId, $customerId]);
    } else {
        $stmt = $pdo->prepare("SELECT * FROM specific_advances WHERE user_id = ?");
        $stmt->execute([$userId]);
    }

    $advances = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Convert types for strict JSON
    $formatted = array_map(function($adv) {
        return [
            'id' => (string)$adv['id'],
            'user_id' => $adv['user_id'],
            'customer_id' => (string)$adv['customer_id'],
            'billing_month' => $adv['billing_month'],
            'amount' => (float)$adv['amount'],
            'is_consumed' => (bool)$adv['is_consumed'],
            'updated_at' => isset($adv['updated_at']) ? (int)$adv['updated_at'] : null
        ];
    }, $advances);

    echo json_encode(["status" => true, "data" => $formatted]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $customerId = $data['customer_id'] ?? ($data['customerId'] ?? null);
    $billingMonth = $data['billing_month'] ?? ($data['billingMonth'] ?? null);
    $amount = isset($data['amount']) ? (float)$data['amount'] : null;

    if ($id === null || $userId === null || $customerId === null || $billingMonth === null || $amount === null ||
        $id === '' || $userId === '' || $billingMonth === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, customer_id, billing_month, and amount are required"]);
        exit;
    }

    ensureSpecificAdvancesSchema($pdo);

    $isConsumed = isset($data['is_consumed']) ? ($data['is_consumed'] ? 1 : 0) :
                  (isset($data['isConsumed']) ? ($data['isConsumed'] ? 1 : 0) : 0);
    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? (time() * 1000));

    // Verify existing record ownership if updating
    $checkStmt = $pdo->prepare("SELECT user_id FROM specific_advances WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this specific advance"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE specific_advances SET 
            customer_id = ?, 
            billing_month = ?, 
            amount = ?, 
            is_consumed = ?, 
            updated_at = ? 
            WHERE id = ? AND user_id = ?");
        $updateStmt->execute([$customerId, $billingMonth, $amount, $isConsumed, $updatedAt, $id, $userId]);

        echo json_encode(["status" => true, "message" => "Specific advance updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO specific_advances (
            id, user_id, customer_id, billing_month, amount, is_consumed, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)");
        $insertStmt->execute([$id, $userId, $customerId, $billingMonth, $amount, $isConsumed, $updatedAt]);

        echo json_encode(["status" => true, "message" => "Specific advance created successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
