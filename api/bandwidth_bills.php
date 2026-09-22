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
    $billingMonth = $_GET['billing_month'] ?? null;
    if ($billingMonth) {
        $canonical = normalizeCanonicalMonth($billingMonth);
        $stmt = $accountPdo->prepare("SELECT * FROM bandwidth_bills WHERE billing_month = ? OR billing_month = ? LIMIT 1");
        $stmt->execute([$canonical, $billingMonth]);
        $bill = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($bill) {
            $bill['user_id'] = $userId;
        }
        echo json_encode(["status" => true, "data" => $bill ? $bill : null]);
    } else {
        $stmt = $accountPdo->query("SELECT * FROM bandwidth_bills ORDER BY billing_month DESC");
        $bills = $stmt->fetchAll(PDO::FETCH_ASSOC);
        foreach ($bills as &$b) {
            $b['user_id'] = $userId;
        }
        echo json_encode(["status" => true, "data" => $bills]);
    }
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $rawBillingMonth = $data['billing_month'] ?? ($data['billingMonth'] ?? null);
    $amount = isset($data['amount']) ? (float)$data['amount'] : null;

    if ($rawBillingMonth === null || $amount === null || $rawBillingMonth === '') {
        echo json_encode(["status" => false, "message" => "billing_month and amount are required"]);
        exit;
    }

    $billingMonth = normalizeCanonicalMonth($rawBillingMonth);
    if (empty($billingMonth)) {
        $billingMonth = (string)$rawBillingMonth;
    }

    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (isset($data['updatedAt']) ? (int)$data['updatedAt'] : (int)(microtime(true) * 1000));

    $checkStmt = $accountPdo->prepare("SELECT billing_month FROM bandwidth_bills WHERE billing_month = ?");
    $checkStmt->execute([$billingMonth]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE bandwidth_bills SET amount = ?, updated_at = ? WHERE billing_month = ?");
        $updateStmt->execute([$amount, $updatedAt, $billingMonth]);
        echo json_encode(["status" => true, "message" => "Bandwidth bill updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO bandwidth_bills (user_id, billing_month, amount, updated_at, created_at) VALUES (?, ?, ?, ?, NOW())");
        $insertStmt->execute([$userId, $billingMonth, $amount, $updatedAt]);
        echo json_encode(["status" => true, "message" => "Bandwidth bill saved successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
