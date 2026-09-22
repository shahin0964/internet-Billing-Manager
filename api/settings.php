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
    $stmt = $accountPdo->query("SELECT * FROM business_settings LIMIT 1");
    $settings = $stmt->fetch(PDO::FETCH_ASSOC);

    if ($settings) {
        $settings['user_id'] = $userId;
        echo json_encode(["status" => true, "data" => $settings]);
    } else {
        echo json_encode(["status" => true, "data" => null]);
    }
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = isset($data['id']) ? intval($data['id']) : 1;
    $ispName = $data['isp_name'] ?? ($data['ispName'] ?? '');
    $hotline = $data['hotline'] ?? '';
    $address = $data['address'] ?? '';
    $currencySymbol = $data['currency_symbol'] ?? ($data['currencySymbol'] ?? '৳');
    $networkStatus = $data['network_status'] ?? ($data['networkStatus'] ?? 'Operational');
    $themeMode = $data['theme_mode'] ?? ($data['themeMode'] ?? 'SYSTEM');
    $logoUri = $data['logo_uri'] ?? ($data['logoUri'] ?? null);
    $email = $data['email'] ?? '';
    $updatedAt = isset($data['updated_at']) ? (int)$data['updated_at'] : (isset($data['updatedAt']) ? (int)$data['updatedAt'] : (int)(microtime(true) * 1000));

    $checkStmt = $accountPdo->query("SELECT id FROM business_settings LIMIT 1");
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE business_settings SET 
            isp_name = ?, 
            hotline = ?, 
            address = ?, 
            currency_symbol = ?, 
            network_status = ?, 
            theme_mode = ?, 
            logo_uri = ?, 
            email = ?, 
            updated_at = ? 
            WHERE id = ?");
        $updateStmt->execute([
            $ispName, $hotline, $address, $currencySymbol, $networkStatus, $themeMode,
            $logoUri, $email, $updatedAt, $existing['id']
        ]);
        echo json_encode(["status" => true, "message" => "Settings updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO business_settings (
            id, user_id, isp_name, hotline, address, currency_symbol, network_status,
            theme_mode, logo_uri, email, updated_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([
            $id, $userId, $ispName, $hotline, $address, $currencySymbol, $networkStatus,
            $themeMode, $logoUri, $email, $updatedAt
        ]);
        echo json_encode(["status" => true, "message" => "Settings created successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
