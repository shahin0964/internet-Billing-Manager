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

function ensureBandwidthBillsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS bandwidth_bills (
            user_id VARCHAR(100) NOT NULL,
            billing_month VARCHAR(100) NOT NULL,
            amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
            updated_at BIGINT NULL DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, billing_month),
            INDEX idx_user_id (user_id),
            INDEX idx_billing_month (billing_month)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM bandwidth_bills");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'amount' => "DECIMAL(10,2) NOT NULL DEFAULT 0.00",
            'updated_at' => "BIGINT NULL DEFAULT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE bandwidth_bills ADD COLUMN $col $definition");
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

    ensureBandwidthBillsSchema($pdo);

    $billingMonth = $_GET['billing_month'] ?? null;
    if ($billingMonth) {
        $stmt = $pdo->prepare("SELECT * FROM bandwidth_bills WHERE user_id = ? AND billing_month = ? LIMIT 1");
        $stmt->execute([$userId, $billingMonth]);
        $bill = $stmt->fetch(PDO::FETCH_ASSOC);
        echo json_encode(["status" => true, "data" => $bill ? $bill : null]);
    } else {
        $stmt = $pdo->prepare("SELECT * FROM bandwidth_bills WHERE user_id = ? ORDER BY billing_month DESC");
        $stmt->execute([$userId]);
        $bills = $stmt->fetchAll(PDO::FETCH_ASSOC);
        echo json_encode(["status" => true, "data" => $bills]);
    }
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $userId = $data['user_id'] ?? null;
    $billingMonth = $data['billing_month'] ?? ($data['billingMonth'] ?? null);
    $amount = isset($data['amount']) ? (float)$data['amount'] : null;

    if ($userId === null || $billingMonth === null || $amount === null || $userId === '' || $billingMonth === '') {
        echo json_encode(["status" => false, "message" => "user_id, billing_month, and amount are required"]);
        exit;
    }

    ensureBandwidthBillsSchema($pdo);

    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? (time() * 1000));

    // Check if record exists for this user_id and billing_month
    $checkStmt = $pdo->prepare("SELECT user_id FROM bandwidth_bills WHERE user_id = ? AND billing_month = ?");
    $checkStmt->execute([$userId, $billingMonth]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $pdo->prepare("UPDATE bandwidth_bills SET amount = ?, updated_at = ? WHERE user_id = ? AND billing_month = ?");
        $updateStmt->execute([$amount, $updatedAt, $userId, $billingMonth]);
        echo json_encode(["status" => true, "message" => "Bandwidth bill updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO bandwidth_bills (user_id, billing_month, amount, updated_at) VALUES (?, ?, ?, ?)");
        $insertStmt->execute([$userId, $billingMonth, $amount, $updatedAt]);
        echo json_encode(["status" => true, "message" => "Bandwidth bill created successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
