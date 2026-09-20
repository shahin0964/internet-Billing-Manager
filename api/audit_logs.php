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

function ensureAuditLogsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS audit_logs (
            id BIGINT NOT NULL,
            user_id VARCHAR(100) NOT NULL,
            action VARCHAR(255) NOT NULL,
            action_type VARCHAR(100) NOT NULL DEFAULT '',
            details TEXT NULL,
            user_email VARCHAR(255) NOT NULL DEFAULT '',
            user_role VARCHAR(100) NOT NULL DEFAULT 'Admin',
            target_entity VARCHAR(100) NOT NULL DEFAULT '',
            target_id VARCHAR(100) NOT NULL DEFAULT '',
            previous_state TEXT NULL,
            new_state TEXT NULL,
            status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
            timestamp BIGINT NOT NULL,
            created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            INDEX idx_user_id (user_id),
            INDEX idx_action_type (action_type),
            INDEX idx_timestamp (timestamp)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM audit_logs");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'action' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'action_type' => "VARCHAR(100) NOT NULL DEFAULT ''",
            'details' => "TEXT NULL",
            'user_email' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'user_role' => "VARCHAR(100) NOT NULL DEFAULT 'Admin'",
            'target_entity' => "VARCHAR(100) NOT NULL DEFAULT ''",
            'target_id' => "VARCHAR(100) NOT NULL DEFAULT ''",
            'previous_state' => "TEXT NULL",
            'new_state' => "TEXT NULL",
            'status' => "VARCHAR(50) NOT NULL DEFAULT 'SUCCESS'",
            'timestamp' => "BIGINT NOT NULL DEFAULT 0"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE audit_logs ADD COLUMN $col $definition");
            }
        }
    } catch (Exception $e) {
        // Continue safely
    }
}

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $userId = $_GET['user_id'] ?? null;
    if (!$userId) {
        echo json_encode(["status" => false, "message" => "user_id is required"]);
        exit;
    }

    ensureAuditLogsSchema($pdo);

    $actionType = $_GET['action_type'] ?? null;
    $limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 500;
    if ($limit <= 0 || $limit > 2000) $limit = 500;

    if ($actionType) {
        $stmt = $pdo->prepare("SELECT * FROM audit_logs WHERE user_id = ? AND action_type = ? ORDER BY timestamp DESC LIMIT $limit");
        $stmt->execute([$userId, $actionType]);
    } else {
        $stmt = $pdo->prepare("SELECT * FROM audit_logs WHERE user_id = ? ORDER BY timestamp DESC LIMIT $limit");
        $stmt->execute([$userId]);
    }
    $logs = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["status" => true, "data" => $logs]);
    exit;
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $id = $data['id'] ?? null;
    $userId = $data['user_id'] ?? null;
    $action = $data['action'] ?? null;

    if ($id === null || $userId === null || $action === null || $id === '' || $userId === '' || $action === '') {
        echo json_encode(["status" => false, "message" => "id, user_id, and action are required"]);
        exit;
    }

    ensureAuditLogsSchema($pdo);

    $actionType = $data['action_type'] ?? ($data['actionType'] ?? '');
    $details = $data['details'] ?? '';
    $userEmail = $data['user_email'] ?? ($data['userEmail'] ?? '');
    $userRole = $data['user_role'] ?? ($data['userRole'] ?? 'Admin');
    $targetEntity = $data['target_entity'] ?? ($data['targetEntity'] ?? '');
    $targetId = $data['target_id'] ?? ($data['targetId'] ?? '');
    $previousState = $data['previous_state'] ?? ($data['previousState'] ?? '');
    $newState = $data['new_state'] ?? ($data['newState'] ?? '');
    $status = $data['status'] ?? 'SUCCESS';
    $timestamp = $data['timestamp'] ?? (time() * 1000);

    // Verify existing record ownership if updating
    $checkStmt = $pdo->prepare("SELECT user_id FROM audit_logs WHERE id = ?");
    $checkStmt->execute([$id]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        if ($existing['user_id'] !== $userId) {
            http_response_code(403);
            echo json_encode(["status" => false, "message" => "Unauthorized to modify this audit log"]);
            exit;
        }

        $updateStmt = $pdo->prepare("UPDATE audit_logs SET 
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
            WHERE id = ? AND user_id = ?");
        $updateStmt->execute([
            $action, $actionType, $details, $userEmail, $userRole,
            $targetEntity, $targetId, $previousState, $newState, $status,
            $timestamp, $id, $userId
        ]);

        echo json_encode(["status" => true, "message" => "Audit log updated successfully"]);
        exit;
    } else {
        $insertStmt = $pdo->prepare("INSERT INTO audit_logs (
            id, user_id, action, action_type, details, user_email, user_role,
            target_entity, target_id, previous_state, new_state, status, timestamp
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
