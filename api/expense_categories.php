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

function ensureExpenseCategoriesSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS expense_categories (
            id BIGINT NOT NULL,
            user_id VARCHAR(100) NOT NULL,
            name VARCHAR(150) NOT NULL,
            updated_at BIGINT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            INDEX idx_user_id (user_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM expense_categories");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'name' => "VARCHAR(150) NOT NULL DEFAULT ''",
            'updated_at' => "BIGINT NULL"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE expense_categories ADD COLUMN $col $definition");
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

    ensureExpenseCategoriesSchema($pdo);

    $stmt = $pdo->prepare("SELECT * FROM expense_categories WHERE user_id = ? ORDER BY name ASC");
    $stmt->execute([$userId]);
    $categories = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $categories]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $name = $data['name'] ?? null;

    if ($id === null || $userId === null || $name === null || $id === '' || $userId === '' || trim($name) === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, and name are required"]);
        exit;
    }

    ensureExpenseCategoriesSchema($pdo);

    $name = trim($name);
    $updatedAt = $data['updated_at'] ?? ($data['updatedAt'] ?? null);

    // Check if category exists to ensure user ownership protection
    $checkStmt = $pdo->prepare("SELECT user_id FROM expense_categories WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this category"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE expense_categories SET name = ?, updated_at = ? WHERE id = ? AND user_id = ?");
        $updateStmt->execute([$name, $updatedAt, $id, $userId]);

        echo json_encode(["status" => true, "message" => "Category updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO expense_categories (id, user_id, name, updated_at) VALUES (?, ?, ?, ?)");
        $insertStmt->execute([$id, $userId, $name, $updatedAt]);

        echo json_encode(["status" => true, "message" => "Category created successfully"]);
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

    ensureExpenseCategoriesSchema($pdo);

    $checkStmt = $pdo->prepare("SELECT user_id FROM expense_categories WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if (!$existing) {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Category not found"]);
        exit;
    }

    if ($existing['user_id'] !== $userId) {
        http_response_code(403);
        echo json_encode(["status" => false, "message" => "Unauthorized to delete this category"]);
        exit;
    }

    $stmt = $pdo->prepare("DELETE FROM expense_categories WHERE id = ? AND user_id = ?");
    $stmt->execute([$id, $userId]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["status" => true, "message" => "Category deleted successfully"]);
    } else {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Category not found"]);
    }
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
