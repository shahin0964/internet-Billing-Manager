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

function ensurePaymentsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        // Create table if it doesn't exist
        $pdo->exec("CREATE TABLE IF NOT EXISTS payments (
            id BIGINT NOT NULL,
            user_id VARCHAR(100) NOT NULL,
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
            PRIMARY KEY (id),
            INDEX idx_user_id (user_id),
            INDEX idx_customer_id (customer_id),
            INDEX idx_bill_id (bill_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Inspect existing columns if table already existed with different structure
        $stmt = $pdo->query("SHOW COLUMNS FROM payments");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'payment_receipt_no' => "VARCHAR(100) NOT NULL DEFAULT ''",
            'bill_id' => "BIGINT NOT NULL DEFAULT 0",
            'customer_id' => "BIGINT NOT NULL DEFAULT 0",
            'customer_name' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'amount' => "DECIMAL(10,2) NOT NULL DEFAULT 0.00",
            'payment_date' => "VARCHAR(50) NOT NULL DEFAULT ''",
            'payment_method' => "VARCHAR(50) NOT NULL DEFAULT 'Cash'",
            'notes' => "TEXT NULL",
            'updated_at' => "BIGINT NULL DEFAULT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE payments ADD COLUMN $col $definition");
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

    ensurePaymentsSchema($pdo);

    $customerId = $_GET['customer_id'] ?? null;
    $billId = $_GET['bill_id'] ?? null;

    if ($customerId && $billId) {
        $stmt = $pdo->prepare("SELECT * FROM payments WHERE user_id = ? AND customer_id = ? AND bill_id = ? ORDER BY id DESC");
        $stmt->execute([$userId, $customerId, $billId]);
    } else if ($customerId) {
        $stmt = $pdo->prepare("SELECT * FROM payments WHERE user_id = ? AND customer_id = ? ORDER BY id DESC");
        $stmt->execute([$userId, $customerId]);
    } else if ($billId) {
        $stmt = $pdo->prepare("SELECT * FROM payments WHERE user_id = ? AND bill_id = ? ORDER BY id DESC");
        $stmt->execute([$userId, $billId]);
    } else {
        $stmt = $pdo->prepare("SELECT * FROM payments WHERE user_id = ? ORDER BY id DESC");
        $stmt->execute([$userId]);
    }
    $payments = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $payments]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $customerId = $data['customer_id'] ?? ($data['customerId'] ?? null);
    $amount = $data['amount'] ?? null;

    if ($id === null || $userId === null || $customerId === null || $amount === null || $id === '' || $userId === '' || $customerId === '' || $amount === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, customer_id and amount are required"]);
        exit;
    }

    ensurePaymentsSchema($pdo);

    $billId = $data['bill_id'] ?? ($data['billId'] ?? 0);
    $paymentReceiptNo = $data['payment_receipt_no'] ?? ($data['paymentReceiptNo'] ?? '');
    $customerName = $data['customer_name'] ?? ($data['customerName'] ?? '');
    $paymentDate = $data['payment_date'] ?? ($data['paymentDate'] ?? '');
    $paymentMethod = $data['payment_method'] ?? ($data['paymentMethod'] ?? 'Cash');
    $notes = $data['notes'] ?? '';
    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? null);

    // Validate customer ownership if customer exists in database
    if ($customerId) {
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
    }

    // Validate bill ownership if bill exists in database
    if ($billId && $billId != 0) {
        try {
            $billStmt = $pdo->prepare("SELECT user_id FROM bills WHERE id = ?");
            $billStmt->execute([$billId]);
            $bill = $billStmt->fetch(PDO::FETCH_ASSOC);
            if ($bill && $bill['user_id'] !== $userId) {
                http_response_code(403);
                echo json_encode(["status" => false, "message" => "Unauthorized: bill belongs to another user"]);
                exit;
            }
        } catch (Exception $e) {
            // Table or relation might not exist, proceed safely
        }
    }

    // Check if payment exists to ensure user ownership protection
    $checkStmt = $pdo->prepare("SELECT user_id FROM payments WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this payment"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE payments SET 
            payment_receipt_no = ?, 
            bill_id = ?, 
            customer_id = ?, 
            customer_name = ?, 
            amount = ?, 
            payment_date = ?, 
            payment_method = ?, 
            notes = ?, 
            updated_at = ? 
            WHERE id = ? AND user_id = ?");
        $updateStmt->execute([
            $paymentReceiptNo, $billId, $customerId, $customerName,
            $amount, $paymentDate, $paymentMethod, $notes,
            $updatedAt, $id, $userId
        ]);

        echo json_encode(["status" => true, "message" => "Payment updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO payments (
            id, user_id, payment_receipt_no, bill_id, customer_id, customer_name,
            amount, payment_date, payment_method, notes, updated_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([
            $id, $userId, $paymentReceiptNo, $billId, $customerId, $customerName,
            $amount, $paymentDate, $paymentMethod, $notes, $updatedAt
        ]);

        echo json_encode(["status" => true, "message" => "Payment created successfully"]);
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

    ensurePaymentsSchema($pdo);

    $checkStmt = $pdo->prepare("SELECT user_id FROM payments WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if (!$existing) {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Payment not found"]);
        exit;
    }

    if ($existing['user_id'] !== $userId) {
        http_response_code(403);
        echo json_encode(["status" => false, "message" => "Unauthorized to delete this payment"]);
        exit;
    }

    $stmt = $pdo->prepare("DELETE FROM payments WHERE id = ? AND user_id = ?");
    $stmt->execute([$id, $userId]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["status" => true, "message" => "Payment deleted successfully"]);
    } else {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Payment not found"]);
    }
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
