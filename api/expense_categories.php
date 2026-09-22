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

$systemPdo = getSystemPdo();
$authenticatedUser = getAuthenticatedUser($systemPdo);
$userId = $authenticatedUser['id'];
$accountPdo = getAccountPdo($systemPdo, $userId);

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $stmt = $accountPdo->query("SELECT * FROM expense_categories ORDER BY name ASC");
    $categories = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($categories as &$c) {
        $c['user_id'] = $userId;
    }

    echo json_encode(["status" => true, "data" => $categories]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $name = $data['name'] ?? null;

    if ($id === null || $name === null || $id === '' || trim($name) === '') {
        echo json_encode(["status" => false, "message" => "id and name are required"]);
        exit;
    }

    $name = trim($name);
    $color = $data['color'] ?? '#6750A4';
    $createdAt = isset($data['created_at']) ? (int)$data['created_at'] : (isset($data['createdAt']) ? (int)$data['createdAt'] : (int)(microtime(true) * 1000));
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (isset($data['updatedAt']) ? (int)$data['updatedAt'] : (int)(microtime(true) * 1000));

    $checkStmt = $accountPdo->prepare("SELECT id FROM expense_categories WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE expense_categories SET name = ?, color = ?, updated_at = ? WHERE id = ?");
        $updateStmt->execute([$name, $color, $updatedAt, $id]);

        echo json_encode(["status" => true, "message" => "Category updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO expense_categories (id, user_id, name, color, created_at, updated_at, created_timestamp) VALUES (?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([$id, $userId, $name, $color, $createdAt, $updatedAt]);

        echo json_encode(["status" => true, "message" => "Category created successfully"]);
        exit;
    }
}

if ($method === 'DELETE') {
    $id = $_GET['id'] ?? null;
    if (!$id) {
        echo json_encode(["status" => false, "message" => "id is required"]);
        exit;
    }

    $stmt = $accountPdo->prepare("DELETE FROM expense_categories WHERE id = ?");
    $stmt->execute([$id]);

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
