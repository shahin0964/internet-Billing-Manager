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

function ensureBillsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $stmt = $pdo->query("SHOW COLUMNS FROM bills");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'bill_number' => "VARCHAR(100) NULL DEFAULT NULL",
            'customer_name' => "VARCHAR(255) NULL DEFAULT NULL",
            'customer_code' => "VARCHAR(100) NULL DEFAULT NULL",
            'paid_amount' => "DECIMAL(10,2) NOT NULL DEFAULT 0.00",
            'due_amount' => "DECIMAL(10,2) NOT NULL DEFAULT 0.00",
            'generated_date' => "VARCHAR(50) NULL DEFAULT NULL",
            'updated_at' => "BIGINT NULL DEFAULT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE bills ADD COLUMN $col $definition");
            }
        }
    } catch (Exception $e) {
        // Continue safely if already exists or permission restricted
    }
}

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $userId = $_GET['user_id'] ?? null;
    if (!$userId) {
        echo json_encode(["status" => false, "message" => "user_id is required"]);
        exit;
    }

    ensureBillsSchema($pdo);

    $customerId = $_GET['customer_id'] ?? null;
    if ($customerId) {
        $stmt = $pdo->prepare("SELECT * FROM bills WHERE user_id = ? AND customer_id = ? ORDER BY id DESC");
        $stmt->execute([$userId, $customerId]);
    } else {
        $stmt = $pdo->prepare("SELECT * FROM bills WHERE user_id = ? ORDER BY id DESC");
        $stmt->execute([$userId]);
    }
    $bills = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $bills]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $customerId = $data['customer_id'] ?? null;
    $amount = $data['amount'] ?? null;

    if ($id === null || $userId === null || $customerId === null || $amount === null || $id === '' || $userId === '' || $customerId === '' || $amount === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, customer_id and amount are required"]);
        exit;
    }

    ensureBillsSchema($pdo);

    $billMonth = $data['bill_month'] ?? ($data['billingMonth'] ?? null);
    $dueDate = $data['due_date'] ?? ($data['dueDate'] ?? null);
    $status = $data['status'] ?? 'unpaid';
    $billNumber = $data['bill_number'] ?? ($data['billNumber'] ?? null);
    $customerName = $data['customer_name'] ?? ($data['customerName'] ?? null);
    $customerCode = $data['customer_code'] ?? ($data['customerCode'] ?? null);
    $paidAmount = $data['paid_amount'] ?? ($data['paidAmount'] ?? 0.0);
    $dueAmount = $data['due_amount'] ?? ($data['dueAmount'] ?? 0.0);
    $generatedDate = $data['generated_date'] ?? ($data['generatedDate'] ?? null);
    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? null);

    // Validate customer ownership if customer exists in database
    try {
        $custStmt = $pdo->prepare("SELECT user_id FROM customers WHERE id = ?");
        $custStmt->execute([$customerId]);
        $cust = $custStmt->fetch(PDO::FETCH_ASSOC);
        if ($cust && $cust['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized: customer belongs to another user"]);
            exit;
        }
    } catch (Exception $e) {
        // Table or relation might not exist, proceed safely
    }

    // Check if bill exists to ensure user ownership protection
    $checkStmt = $pdo->prepare("SELECT user_id FROM bills WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this bill"]);
            exit;
        }

        // Inspect available columns
        $stmt = $pdo->query("SHOW COLUMNS FROM bills");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        if (in_array('bill_number', $existingCols)) {
            $updateStmt = $pdo->prepare("UPDATE bills SET 
                customer_id = ?, 
                amount = ?, 
                bill_month = ?, 
                due_date = ?, 
                status = ?, 
                bill_number = ?, 
                customer_name = ?, 
                customer_code = ?, 
                paid_amount = ?, 
                due_amount = ?, 
                generated_date = ?, 
                updated_at = ? 
                WHERE id = ? AND user_id = ?");
            $updateStmt->execute([
                $customerId, $amount, $billMonth, $dueDate, $status,
                $billNumber, $customerName, $customerCode, $paidAmount, $dueAmount,
                $generatedDate, $updatedAt, $id, $userId
            ]);
        } else {
            $updateStmt = $pdo->prepare("UPDATE bills SET 
                customer_id = ?, 
                amount = ?, 
                bill_month = ?, 
                due_date = ?, 
                status = ? 
                WHERE id = ? AND user_id = ?");
            $updateStmt->execute([
                $customerId, $amount, $billMonth, $dueDate, $status, $id, $userId
            ]);
        }

        echo json_encode(["status" => true, "message" => "Bill updated successfully"]);
        exit;
    } else {
        // Inspect available columns
        $stmt = $pdo->query("SHOW COLUMNS FROM bills");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        if (in_array('bill_number', $existingCols)) {
            $insertStmt = $pdo->prepare("INSERT INTO bills (
                id, user_id, customer_id, amount, bill_month, due_date, status,
                bill_number, customer_name, customer_code, paid_amount, due_amount,
                generated_date, updated_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
            $insertStmt->execute([
                $id, $userId, $customerId, $amount, $billMonth, $dueDate, $status,
                $billNumber, $customerName, $customerCode, $paidAmount, $dueAmount,
                $generatedDate, $updatedAt
            ]);
        } else {
            $insertStmt = $pdo->prepare("INSERT INTO bills (
                id, user_id, customer_id, amount, bill_month, due_date, status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())");
            $insertStmt->execute([
                $id, $userId, $customerId, $amount, $billMonth, $dueDate, $status
            ]);
        }

        echo json_encode(["status" => true, "message" => "Bill created successfully"]);
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

    $checkStmt = $pdo->prepare("SELECT user_id FROM bills WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if (!$existing) {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Bill not found"]);
        exit;
    }

    if ($existing['user_id'] !== $userId) {
        http_response_code(403);
        echo json_encode(["status" => false, "message" => "Unauthorized to delete this bill"]);
        exit;
    }

    $stmt = $pdo->prepare("DELETE FROM bills WHERE id = ? AND user_id = ?");
    $stmt->execute([$id, $userId]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["status" => true, "message" => "Bill deleted successfully"]);
    } else {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Bill not found"]);
    }
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
