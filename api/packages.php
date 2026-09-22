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
    $stmt = $accountPdo->query("SELECT id, name, price, speed, created_at, updated_at FROM packages ORDER BY id ASC");
    $packages = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($packages as &$p) {
        $p['user_id'] = $userId;
    }

    echo json_encode(["status" => true, "data" => $packages]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $name = $data['name'] ?? null;
    $price = $data['price'] ?? null;
    $speed = $data['speed'] ?? null;
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (int)(microtime(true) * 1000);

    if ($id === null || $name === null || $price === null || $id === '' || $name === '' || $price === '') {
        echo json_encode(["status" => false, "message" => "id, name and price are required"]);
        exit;
    }

    $checkStmt = $accountPdo->prepare("SELECT id FROM packages WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE packages SET name = ?, price = ?, speed = ?, updated_at = ? WHERE id = ?");
        $updateStmt->execute([$name, $price, $speed, $updatedAt, $id]);
        echo json_encode(["status" => true, "message" => "Package updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO packages (id, user_id, name, price, speed, updated_at, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([$id, $userId, $name, $price, $speed, $updatedAt]);
        echo json_encode(["status" => true, "message" => "Package created successfully"]);
        exit;
    }
}

if ($method === 'DELETE') {
    $id = $_GET['id'] ?? null;
    if (!$id) {
        echo json_encode(["status" => false, "message" => "id is required"]);
        exit;
    }

    $stmt = $accountPdo->prepare("DELETE FROM packages WHERE id = ?");
    $stmt->execute([$id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["status" => true, "message" => "Package deleted successfully"]);
    } else {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Package not found"]);
    }
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
