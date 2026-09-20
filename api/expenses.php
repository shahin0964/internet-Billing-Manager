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

function ensureExpensesSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS expenses (
            id BIGINT NOT NULL,
            user_id VARCHAR(100) NOT NULL,
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
            PRIMARY KEY (id),
            INDEX idx_user_id (user_id),
            INDEX idx_category (category),
            INDEX idx_date (date)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM expenses");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'title' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'amount' => "DECIMAL(10,2) NOT NULL DEFAULT 0.00",
            'category' => "VARCHAR(100) NOT NULL DEFAULT 'General'",
            'date' => "VARCHAR(50) NOT NULL DEFAULT ''",
            'payment_method' => "VARCHAR(50) NOT NULL DEFAULT 'Cash'",
            'note' => "TEXT NULL",
            'receipt_path' => "VARCHAR(255) NULL",
            'created_at' => "BIGINT NULL",
            'updated_at' => "BIGINT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE expenses ADD COLUMN $col $definition");
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

    ensureExpensesSchema($pdo);

    $category = $_GET['category'] ?? null;

    if ($category) {
        $stmt = $pdo->prepare("SELECT * FROM expenses WHERE user_id = ? AND category = ? ORDER BY date DESC, id DESC");
        $stmt->execute([$userId, $category]);
    } else {
        $stmt = $pdo->prepare("SELECT * FROM expenses WHERE user_id = ? ORDER BY date DESC, id DESC");
        $stmt->execute([$userId]);
    }
    $expenses = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $expenses]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $title = $data['title'] ?? null;
    $amount = $data['amount'] ?? null;

    if ($id === null || $userId === null || $title === null || $amount === null || $id === '' || $userId === '' || $title === '' || $amount === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, title, and amount are required"]);
        exit;
    }

    ensureExpensesSchema($pdo);

    $category = $data['category'] ?? 'General';
    $date = $data['date'] ?? '';
    $paymentMethod = $data['payment_method'] ?? ($data['paymentMethod'] ?? 'Cash');
    $note = $data['note'] ?? '';
    $receiptPath = $data['receipt_path'] ?? ($data['receiptPath'] ?? null);
    $createdAt = $data['created_at'] ?? ($data['createdAt'] ?? null);
    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? null);

    // Check if expense exists to ensure user ownership protection
    $checkStmt = $pdo->prepare("SELECT user_id FROM expenses WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this expense"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE expenses SET 
            title = ?, 
            amount = ?, 
            category = ?, 
            date = ?, 
            payment_method = ?, 
            note = ?, 
            receipt_path = ?, 
            updated_at = ? 
            WHERE id = ? AND user_id = ?");
        $updateStmt->execute([
            $title, $amount, $category, $date,
            $paymentMethod, $note, $receiptPath,
            $updatedAt, $id, $userId
        ]);

        echo json_encode(["status" => true, "message" => "Expense updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO expenses (
            id, user_id, title, amount, category, date,
            payment_method, note, receipt_path, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $insertStmt->execute([
            $id, $userId, $title, $amount, $category, $date,
            $paymentMethod, $note, $receiptPath, $createdAt, $updatedAt
        ]);

        echo json_encode(["status" => true, "message" => "Expense created successfully"]);
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

    ensureExpensesSchema($pdo);

    $checkStmt = $pdo->prepare("SELECT user_id FROM expenses WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if (!$existing) {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Expense not found"]);
        exit;
    }

    if ($existing['user_id'] !== $userId) {
        http_response_code(403);
        echo json_encode(["status" => false, "message" => "Unauthorized to delete this expense"]);
        exit;
    }

    $stmt = $pdo->prepare("DELETE FROM expenses WHERE id = ? AND user_id = ?");
    $stmt->execute([$id, $userId]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["status" => true, "message" => "Expense deleted successfully"]);
    } else {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Expense not found"]);
    }
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
