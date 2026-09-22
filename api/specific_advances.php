<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
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
    $billingMonth = $_GET['billing_month'] ?? null;

    if ($customerId && $billingMonth) {
        $canonical = normalizeCanonicalMonth($billingMonth);
        $stmt = $accountPdo->prepare("SELECT * FROM specific_advances WHERE customer_id = ? AND (billing_month = ? OR billing_month = ?)");
        $stmt->execute([$customerId, $canonical, $billingMonth]);
    } elseif ($customerId) {
        $stmt = $accountPdo->prepare("SELECT * FROM specific_advances WHERE customer_id = ?");
        $stmt->execute([$customerId]);
    } else {
        $stmt = $accountPdo->query("SELECT * FROM specific_advances");
    }

    $advances = $stmt->fetchAll(PDO::FETCH_ASSOC);

    $formatted = array_map(function($adv) use ($userId) {
        return [
            'id' => (string)$adv['id'],
            'user_id' => $userId,
            'customer_id' => (string)$adv['customer_id'],
            'billing_month' => $adv['billing_month'],
            'amount' => (float)$adv['amount'],
            'is_consumed' => (bool)$adv['is_consumed'],
            'updated_at' => isset($adv['updated_at']) ? (int)$adv['updated_at'] : null
        ];
    }, $advances);

    echo json_encode(["status" => true, "data" => $formatted]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $customerId = $data['customer_id'] ?? ($data['customerId'] ?? null);
    $rawBillingMonth = $data['billing_month'] ?? ($data['billingMonth'] ?? null);
    $amount = isset($data['amount']) ? (float)$data['amount'] : null;

    if ($id === null || $customerId === null || $rawBillingMonth === null || $amount === null || $id === '' || $customerId === '' || $rawBillingMonth === '') {
        echo json_encode(["status" => false, "message" => "id, customer_id, billing_month, and amount are required"]);
        exit;
    }

    $billingMonth = normalizeCanonicalMonth($rawBillingMonth);
    if (empty($billingMonth)) {
        $billingMonth = (string)$rawBillingMonth;
    }

    $isConsumed = isset($data['is_consumed']) ? ($data['is_consumed'] ? 1 : 0) : (isset($data['isConsumed']) ? ($data['isConsumed'] ? 1 : 0) : 0);
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (isset($data['updatedAt']) ? (int)$data['updatedAt'] : (int)(microtime(true) * 1000));

    $checkStmt = $accountPdo->prepare("SELECT id FROM specific_advances WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE specific_advances SET customer_id = ?, billing_month = ?, amount = ?, is_consumed = ?, updated_at = ? WHERE id = ?");
        $updateStmt->execute([$customerId, $billingMonth, $amount, $isConsumed, $updatedAt, $id]);
        echo json_encode(["status" => true, "message" => "Specific advance updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO specific_advances (id, user_id, customer_id, billing_month, amount, is_consumed, updated_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([$id, $userId, $customerId, $billingMonth, $amount, $isConsumed, $updatedAt]);
        echo json_encode(["status" => true, "message" => "Specific advance created successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
