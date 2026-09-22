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
    $actionType = $_GET['action_type'] ?? null;
    $limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 500;
    if ($limit <= 0 || $limit > 2000) $limit = 500;

    if ($actionType) {
        $stmt = $accountPdo->prepare("SELECT * FROM audit_logs WHERE action_type = ? ORDER BY timestamp DESC LIMIT $limit");
        $stmt->execute([$actionType]);
    } else {
        $stmt = $accountPdo->query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT $limit");
    }
    $logs = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($logs as &$l) {
        $l['user_id'] = $userId;
    }

    echo json_encode(["status" => true, "data" => $logs]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $action = $data['action'] ?? null;

    if ($id === null || $action === null || $id === '' || trim($action) === '') {
        echo json_encode(["status" => false, "message" => "id and action are required"]);
        exit;
    }

    $actionType = $data['action_type'] ?? ($data['actionType'] ?? 'GENERAL');
    $details = $data['details'] ?? '';
    $userEmail = $data['user_email'] ?? ($data['userEmail'] ?? $authenticatedUser['email']);
    $userRole = $data['user_role'] ?? ($data['userRole'] ?? ($authenticatedUser['role'] ?? 'Admin'));
    $targetEntity = $data['target_entity'] ?? ($data['targetEntity'] ?? '');
    $targetId = $data['target_id'] ?? ($data['targetId'] ?? '');
    $previousState = $data['previous_state'] ?? ($data['previousState'] ?? null);
    $newState = $data['new_state'] ?? ($data['newState'] ?? null);
    $status = $data['status'] ?? 'SUCCESS';
    $timestamp = isset($data['timestamp']) ? (int)$data['timestamp'] : (int)(microtime(true) * 1000);

    $checkStmt = $accountPdo->prepare("SELECT id FROM audit_logs WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        $updateStmt = $accountPdo->prepare("UPDATE audit_logs SET 
            action = ?, 
            action_type = ?, 
            details = ?, 
            user_email = ?, 
            user_role = ?, 
            target_entity = ?, 
            target_id = ?, 
            previous_state = ?, 
            new_state = ?, 
            status = ?, 
            timestamp = ? 
            WHERE id = ?");
        $updateStmt->execute([
            $action, $actionType, $details, $userEmail, $userRole,
            $targetEntity, $targetId, $previousState, $newState, $status,
            $timestamp, $id
        ]);

        echo json_encode(["status" => true, "message" => "Audit log updated successfully"]);
        exit;
    } else {
        $insertStmt = $accountPdo->prepare("INSERT INTO audit_logs (
            id, user_id, action, action_type, details, user_email, user_role,
            target_entity, target_id, previous_state, new_state, status, timestamp, created_timestamp
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $insertStmt->execute([
            $id, $userId, $action, $actionType, $details, $userEmail, $userRole,
            $targetEntity, $targetId, $previousState, $newState, $status, $timestamp
        ]);

        echo json_encode(["status" => true, "message" => "Audit log created successfully"]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
