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

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $userId = $_GET['user_id'] ?? null;
    if (!$userId) {
        echo json_encode(["status" => false, "message" => "user_id is required"]);
        exit;
    }

    $stmt = $pdo->prepare("SELECT id, user_id, name, price, speed, created_at FROM packages WHERE user_id = ? ORDER BY id ASC");
    $stmt->execute([$userId]);
    $packages = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $packages]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $name = $data['name'] ?? null;
    $price = $data['price'] ?? null;
    $speed = $data['speed'] ?? null;

    if ($id === null || $userId === null || $name === null || $price === null || $id === '' || $userId === '' || $name === '' || $price === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, name and price are required"]);
        exit;
    }

    // Check if package exists to ensure user ownership protection
    $checkStmt = $pdo->prepare("SELECT user_id FROM packages WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this package"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE packages SET name = ?, price = ?, speed = ? WHERE id = ? AND user_id = ?");
        $updateStmt->execute([$name, $price, $speed, $id, $userId]);
        echo json_encode(["status" => true, "message" => "Package updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO packages (id, user_id, name, price, speed, created_at) VALUES (?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([$id, $userId, $name, $price, $speed]);
        echo json_encode(["status" => true, "message" => "Package created successfully"]);
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

    $stmt = $pdo->prepare("DELETE FROM packages WHERE id = ? AND user_id = ?");
    $stmt->execute([$id, $userId]);

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
