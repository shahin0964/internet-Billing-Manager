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
    if ($customerId) {
        $stmt = $accountPdo->prepare("SELECT * FROM bills WHERE customer_id = ? ORDER BY id DESC");
        $stmt->execute([$customerId]);
    } else {
        $stmt = $accountPdo->query("SELECT * FROM bills ORDER BY id DESC");
    }
    $bills = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($bills as &$b) {
        $b['user_id'] = $userId;
    }

    echo json_encode(["status" => true, "data" => $bills]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $customerId = $data['customer_id'] ?? null;
    $amount = $data['amount'] ?? null;

    if ($id === null || $customerId === null || $amount === null || $id === '' || $customerId === '' || $amount === '') {
        echo json_encode(["status" => false, "message" => "id, customer_id and amount are required"]);
        exit;
    }

    $rawMonth = $data['month'] ?? ($data['bill_month'] ?? ($data['billingMonth'] ?? ''));
    $canonicalMonth = normalizeCanonicalMonth($rawMonth);
    if (empty($canonicalMonth)) {
        $canonicalMonth = date('Y-m');
    }

    $dueDate = $data['due_date'] ?? ($data['dueDate'] ?? '');
    $status = strtoupper(trim($data['status'] ?? 'UNPAID'));
    $billNumber = $data['bill_number'] ?? ($data['billNumber'] ?? null);
    $customerName = $data['customer_name'] ?? ($data['customerName'] ?? null);
    $customerCode = $data['customer_code'] ?? ($data['customerCode'] ?? null);
    $paidAmount = isset($data['paid_amount']) ? (float)$data['paid_amount'] : (isset($data['paidAmount']) ? (float)$data['paidAmount'] : 0.0);
    $dueAmount = isset($data['due_amount']) ? (float)$data['due_amount'] : (isset($data['dueAmount']) ? (float)$data['dueAmount'] : (float)$amount);
    $generatedDate = $data['generated_date'] ?? ($data['generatedDate'] ?? date('Y-m-d'));
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (int)(microtime(true) * 1000);

    // 1. Check if bill exists by primary key ID in this user's database
    $checkStmt = $accountPdo->prepare("SELECT * FROM bills WHERE id = ?");
    $checkStmt->execute([$id]);
    $existingById = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existingById) {
        $updateStmt = $accountPdo->prepare("UPDATE bills SET 
            customer_id = ?, 
            amount = ?, 
            month = ?,
            bill_month = ?, 
            due_date = ?, 
            status = ?, 
            bill_number = ?, 
            customer_name = ?, 
            customer_code = ?, 
            paid_amount = ?, 
            due_amount = ?, 
            generated_date = ?, 
            updated_at = ? 
            WHERE id = ?");
        $updateStmt->execute([
            $customerId, $amount, $canonicalMonth, $canonicalMonth, $dueDate, $status,
            $billNumber, $customerName, $customerCode, $paidAmount, $dueAmount,
            $generatedDate, $updatedAt, $id
        ]);

        echo json_encode(["status" => true, "message" => "Bill updated successfully"]);
        exit;
    }

    // 2. Strict Business Rule: One Customer + One Billing Period = Exactly One Bill
    // Check if a bill already exists for this customer and canonical billing month
    $monthCheckStmt = $accountPdo->prepare("SELECT id, status, amount, paid_amount, due_amount FROM bills WHERE customer_id = ? AND (month = ? OR bill_month = ?) LIMIT 1");
    $monthCheckStmt->execute([$customerId, $canonicalMonth, $canonicalMonth]);
    $existingByMonth = $monthCheckStmt->fetch(PDO::FETCH_ASSOC);

    if ($existingByMonth) {
        // Idempotent return: do not generate duplicate bill for the same period
        echo json_encode([
            "status" => true,
            "message" => "Bill for this month already exists (idempotent skipped duplicate).",
            "existing_id" => $existingByMonth['id'],
            "status_code" => $existingByMonth['status']
        ]);
        exit;
    }

    // 3. Safe Insert
    try {
        $insertStmt = $accountPdo->prepare("INSERT INTO bills (
            id, user_id, customer_id, amount, month, bill_month, due_date, status,
            bill_number, customer_name, customer_code, paid_amount, due_amount,
            generated_date, updated_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([
            $id, $userId, $customerId, $amount, $canonicalMonth, $canonicalMonth, $dueDate, $status,
            $billNumber, $customerName, $customerCode, $paidAmount, $dueAmount,
            $generatedDate, $updatedAt
        ]);

        echo json_encode(["status" => true, "message" => "Bill created successfully"]);
        exit;
    } catch (PDOException $e) {
        if ($e->getCode() == 23000 || strpos($e->getMessage(), 'Duplicate entry') !== false) {
            // Handled race condition: duplicate key constraint caught
            echo json_encode(["status" => true, "message" => "Bill already exists for this period."]);
            exit;
        }
        throw $e;
    }
}

if ($method === 'DELETE') {
    $id = $_GET['id'] ?? null;
    if (!$id) {
        echo json_encode(["status" => false, "message" => "id is required"]);
        exit;
    }

    $stmt = $accountPdo->prepare("DELETE FROM bills WHERE id = ?");
    $stmt->execute([$id]);

    if ($stmt->rowCount() > 0) {
        echo json_encode(["status" => true, "message" => "Bill deleted successfully"]);
    } else {
        http_response_code(404);
        echo json_encode(["status" => false, "message" => "Bill not found"]);
    }
    exit;
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
