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
    $category = $_GET['category'] ?? null;

    if ($category) {
        $stmt = $accountPdo->prepare("SELECT * FROM expenses WHERE category = ? ORDER BY date DESC, id DESC");
        $stmt->execute([$category]);
    } else {
        $stmt = $accountPdo->query("SELECT * FROM expenses ORDER BY date DESC, id DESC");
    }
    $expenses = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($expenses as &$e) {
        $e['user_id'] = $userId;
    }

    echo json_encode(["status" => true, "data" => $expenses]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $title = $data['title'] ?? null;
    $amount = $data['amount'] ?? null;

    if ($id === null || $title === null || $amount === null || $id === '' || $title === '' || $amount === '') {
        echo json_encode(["status" => false, "message" => "id, title, and amount are required"]);
        exit;
    }

    $category = $data['category'] ?? 'General';
    $date = $data['date'] ?? date('Y-m-d');
    $paymentMethod = $data['payment_method'] ?? ($data['paymentMethod'] ?? 'Cash');
    $note = $data['note'] ?? '';
    $receiptPath = $data['receipt_path'] ?? ($data['receiptPath'] ?? null);
    $createdAt = isset($data['created_at']) ? (int)$data['created_at'] : (isset($data['createdAt']) ? (int)$data['createdAt'] : (int)(microtime(true) * 1000));
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (isset($data['updatedAt']) ? (int)$data['updatedAt'] : (int)(microtime(true) * 1000));

    $checkStmt = $accountPdo->prepare("SELECT id FROM expenses WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE expenses SET 
            title = ?, 
            amount = ?, 
            category = ?, 
            date = ?, 
            payment_method = ?, 
            note = ?, 
            receipt_path = ?, 
            updated_at = ? 
            WHERE id = ?");
        $updateStmt->execute([$title, $amount, $category, $date, $paymentMethod, $note, $receiptPath, $updatedAt, $id]);

        echo json_encode(["status" => true, "message" => "Expense updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO expenses (
            id, user_id, title, amount, category, date, payment_method, note, receipt_path, created_at, updated_at, created_timestamp
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([
            $id, $userId, $title, $amount, $category, $date, $paymentMethod, $note, $receiptPath, $createdAt, $updatedAt
        ]);

        echo json_encode(["status" => true, "message" => "Expense created successfully"]);
        exit;
    }
}

if ($method === 'DELETE') {
    $id = $_GET['id'] ?? null;
    if (!$id) {
        echo json_encode(["status" => false, "message" => "id is required"]);
        exit;
    }

    $stmt = $accountPdo->prepare("DELETE FROM expenses WHERE id = ?");
    $stmt->execute([$id]);

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
