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
    $customerId = $_GET['customer_id'] ?? null;
    $billId = $_GET['bill_id'] ?? null;

    if ($customerId && $billId) {
        $stmt = $accountPdo->prepare("SELECT * FROM payments WHERE customer_id = ? AND bill_id = ? ORDER BY id DESC");
        $stmt->execute([$customerId, $billId]);
    } else if ($customerId) {
        $stmt = $accountPdo->prepare("SELECT * FROM payments WHERE customer_id = ? ORDER BY id DESC");
        $stmt->execute([$customerId]);
    } else if ($billId) {
        $stmt = $accountPdo->prepare("SELECT * FROM payments WHERE bill_id = ? ORDER BY id DESC");
        $stmt->execute([$billId]);
    } else {
        $stmt = $accountPdo->query("SELECT * FROM payments ORDER BY id DESC");
    }
    $payments = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($payments as &$p) {
        $p['user_id'] = $userId;
    }

    echo json_encode(["status" => true, "data" => $payments]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $customerId = $data['customer_id'] ?? ($data['customerId'] ?? null);
    $amount = $data['amount'] ?? null;

    if ($id === null || $customerId === null || $amount === null || $id === '' || $customerId === '' || $amount === '') {
        echo json_encode(["status" => false, "message" => "id, customer_id and amount are required"]);
        exit;
    }

    $billId = $data['bill_id'] ?? ($data['billId'] ?? 0);
    $paymentReceiptNo = $data['payment_receipt_no'] ?? ($data['paymentReceiptNo'] ?? '');
    $customerName = $data['customer_name'] ?? ($data['customerName'] ?? '');
    $paymentDate = $data['payment_date'] ?? ($data['paymentDate'] ?? '');
    $paymentMethod = $data['payment_method'] ?? ($data['paymentMethod'] ?? 'Cash');
    $notes = $data['notes'] ?? '';
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (isset($data['updatedAt']) ? (int)$data['updatedAt'] : (int)(microtime(true) * 1000));

    $checkStmt = $accountPdo->prepare("SELECT id FROM payments WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE payments SET 
            payment_receipt_no = ?, 
            bill_id = ?, 
            customer_id = ?, 
            customer_name = ?, 
            amount = ?, 
            payment_date = ?, 
            payment_method = ?, 
            notes = ?, 
            updated_at = ? 
            WHERE id = ?");
        $updateStmt->execute([
            $paymentReceiptNo, $billId, $customerId, $customerName,
            $amount, $paymentDate, $paymentMethod, $notes,
            $updatedAt, $id
        ]);

        echo json_encode(["status" => true, "message" => "Payment updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO payments (
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
    if (!$id) {
        echo json_encode(["status" => false, "message" => "id is required"]);
        exit;
    }

    $stmt = $accountPdo->prepare("DELETE FROM payments WHERE id = ?");
    $stmt->execute([$id]);

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
