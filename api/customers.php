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
    try {
        $stmt = $accountPdo->query("SELECT id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, created_at, updated_at FROM customers ORDER BY name ASC");
        $customers = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // Inject user_id for client compatibility
        foreach ($customers as &$c) {
            $c['user_id'] = $userId;
        }

        echo json_encode(["status" => true, "data" => $customers]);
        exit;
    } catch (Exception $e) {
        echo json_encode(["status" => false, "message" => "Error fetching customers: " . $e->getMessage()]);
        exit;
    }
}

if ($method === 'POST') {
    try {
        $raw = file_get_contents("php://input");
        $data = json_decode($raw, true);

        $id = $data['id'] ?? null;
        $name = $data['name'] ?? null;
        $phone = $data['phone'] ?? '';
        $address = $data['address'] ?? '';
        $ipAddress = $data['ip_address'] ?? '';
        $packageId = $data['package_id'] ?? '';
        $billingCycleDate = isset($data['billing_cycle_date']) ? (int)$data['billing_cycle_date'] : 1;
        $status = $data['status'] ?? 'ACTIVE';
        $pppoeUsername = $data['pppoe_username'] ?? '';
        $customerCode = $data['customer_code'] ?? '';
        $joiningDate = $data['joining_date'] ?? '';
        $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (int)(microtime(true) * 1000);

        if ($id === null || $name === null || $id === '' || $name === '') {
            echo json_encode(["status" => false, "message" => "id and name are required"]);
            exit;
        }

        // Check existing customer in this user's isolated database
        $checkStmt = $accountPdo->prepare("SELECT id FROM customers WHERE id = ?");
        $checkStmt->execute([$id]);
        $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

        if ($existing) {
            $updateStmt = $accountPdo->prepare("UPDATE customers SET name = ?, phone = ?, address = ?, ip_address = ?, package_id = ?, billing_cycle_date = ?, status = ?, pppoe_username = ?, customer_code = ?, joining_date = ?, updated_at = ? WHERE id = ?");
            $updateStmt->execute([$name, $phone, $address, $ipAddress, $packageId, $billingCycleDate, $status, $pppoeUsername, $customerCode, $joiningDate, $updatedAt, $id]);
            echo json_encode(["status" => true, "message" => "Customer updated successfully"]);
            exit;
        } else {
            $insertStmt = $accountPdo->prepare("INSERT INTO customers (id, user_id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            $insertStmt->execute([$id, $userId, $name, $phone, $address, $ipAddress, $packageId, $billingCycleDate, $status, $pppoeUsername, $customerCode, $joiningDate, $updatedAt]);
            echo json_encode(["status" => true, "message" => "Customer added successfully"]);
            exit;
        }
    } catch (Exception $e) {
        echo json_encode(["status" => false, "message" => "Error saving customer: " . $e->getMessage()]);
        exit;
    }
}

if ($method === 'DELETE') {
    try {
        $id = $_GET['id'] ?? null;
        if (!$id) {
            echo json_encode(["status" => false, "message" => "id is required"]);
            exit;
        }

        $deleteStmt = $accountPdo->prepare("DELETE FROM customers WHERE id = ?");
        $deleteStmt->execute([$id]);

        echo json_encode(["status" => true, "message" => "Customer deleted successfully"]);
        exit;
    } catch (Exception $e) {
        echo json_encode(["status" => false, "message" => "Error deleting customer: " . $e->getMessage()]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
