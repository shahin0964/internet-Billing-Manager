<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once 'db.php';

function ensureCustomersSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS customers (
            id VARCHAR(100) NOT NULL,
            user_id VARCHAR(100) NOT NULL,
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
            PRIMARY KEY (id),
            INDEX idx_user_id (user_id),
            INDEX idx_updated_at (updated_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM customers");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'phone' => "VARCHAR(50) NULL DEFAULT ''",
            'address' => "TEXT NULL DEFAULT NULL",
            'ip_address' => "VARCHAR(100) NULL DEFAULT ''",
            'package_id' => "VARCHAR(100) NULL DEFAULT ''",
            'billing_cycle_date' => "INT NOT NULL DEFAULT 1",
            'status' => "VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'",
            'pppoe_username' => "VARCHAR(100) NULL DEFAULT ''",
            'customer_code' => "VARCHAR(100) NULL DEFAULT ''",
            'joining_date' => "VARCHAR(50) NULL DEFAULT ''",
            'updated_at' => "BIGINT NULL DEFAULT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE customers ADD COLUMN $col $definition");
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

    ensureCustomersSchema($pdo);

    $stmt = $pdo->prepare("SELECT id, user_id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, created_at, updated_at FROM customers WHERE user_id = ? ORDER BY name ASC");
    $stmt->execute([$userId]);
    $customers = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $customers]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $name = $data['name'] ?? null;
    $phone = $data['phone'] ?? '';
    $address = $data['address'] ?? '';
    $ipAddress = $data['ip_address'] ?? '';
    $packageId = $data['package_id'] ?? '';
    $billingCycleDate = isset($data['billing_cycle_date']) ? (int)$data['billing_cycle_date'] : 1;
    $status = $data['status'] ?? 'ACTIVE';
    $pppoeUsername = $data['pppoe_username'] ?? '';
    $customerCode = $data['customer_code'] ?? '';
    $joiningDate = $data['joining_date'] ?? '';
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (int)(microtime(true) * 1000);

    if ($id === null || $userId === null || $name === null || $id === '' || $userId === '' || $name === '') {
        echo json_encode(["status" => false, "message" => "id, user_id and name are required"]);
        exit;
    }

    ensureCustomersSchema($pdo);

    // Check existing customer
    $checkStmt = $pdo->prepare("SELECT user_id FROM customers WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this customer"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE customers SET name = ?, phone = ?, address = ?, ip_address = ?, package_id = ?, billing_cycle_date = ?, status = ?, pppoe_username = ?, customer_code = ?, joining_date = ?, updated_at = ? WHERE id = ? AND user_id = ?");
        $updateStmt->execute([$name, $phone, $address, $ipAddress, $packageId, $billingCycleDate, $status, $pppoeUsername, $customerCode, $joiningDate, $updatedAt, $id, $userId]);
        echo json_encode(["status" => true, "message" => "Customer updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO customers (id, user_id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $insertStmt->execute([$id, $userId, $name, $phone, $address, $ipAddress, $packageId, $billingCycleDate, $status, $pppoeUsername, $customerCode, $joiningDate, $updatedAt]);
        echo json_encode(["status" => true, "message" => "Customer added successfully"]);
        exit;
    }
}

if ($method === 'DELETE') {
    $id = $_GET['id'] ?? null;
    $userId = $_GET['user_id'] ?? null;

    if (!$id || !$userId) {
        echo json_encode(["status" => false, "message" => "id and user_id are required"]);
        exit;
    }

    ensureCustomersSchema($pdo);

    $deleteStmt = $pdo->prepare("DELETE FROM customers WHERE id = ? AND user_id = ?");
    $deleteStmt->execute([$id, $userId]);

    echo json_encode(["status" => true, "message" => "Customer deleted successfully"]);
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
