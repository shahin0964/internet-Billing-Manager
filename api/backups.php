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
    try {
        $action = $_GET['action'] ?? 'latest';

        if ($action === 'latest') {
            $stmt = $accountPdo->query("SELECT id, backup_name, backup_data, backup_size, version, UNIX_TIMESTAMP(created_at) * 1000 AS created_at FROM cloud_backups ORDER BY created_at DESC LIMIT 1");
            $backup = $stmt->fetch(PDO::FETCH_ASSOC);

            if (!$backup) {
                echo json_encode(["status" => false, "message" => "No backup found for this user", "data" => null]);
                exit;
            }

            echo json_encode([
                "status" => true,
                "message" => "Backup retrieved successfully",
                "data" => [
                    "id" => (string)$backup['id'],
                    "user_id" => $userId,
                    "backup_name" => $backup['backup_name'],
                    "backup_data" => $backup['backup_data'],
                    "backup_size" => (int)$backup['backup_size'],
                    "version" => (int)$backup['version'],
                    "created_at" => (int)$backup['created_at']
                ]
            ]);
            exit;
        } elseif ($action === 'list') {
            $stmt = $accountPdo->query("SELECT id, backup_name, backup_size, version, UNIX_TIMESTAMP(created_at) * 1000 AS created_at FROM cloud_backups ORDER BY created_at DESC LIMIT 10");
            $backups = $stmt->fetchAll(PDO::FETCH_ASSOC);

            $formatted = array_map(function($b) use ($userId) {
                return [
                    "id" => (string)$b['id'],
                    "user_id" => $userId,
                    "backup_name" => $b['backup_name'],
                    "backup_size" => (int)$b['backup_size'],
                    "version" => (int)$b['version'],
                    "created_at" => (int)$b['created_at']
                ];
            }, $backups);

            echo json_encode(["status" => true, "data" => $formatted]);
            exit;
        }
    } catch (Exception $e) {
        echo json_encode(["status" => false, "message" => "Database error: " . $e->getMessage(), "data" => null]);
        exit;
    }
}

if ($method === 'POST') {
    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true);

    $backupData = $data['backup_data'] ?? null;
    $backupName = $data['backup_name'] ?? ('ISP-Cloud-Backup-' . date('Y-m-d-H-i-s'));
    $version = isset($data['version']) ? (int)$data['version'] : 1;

    if (!$backupData) {
        echo json_encode(["status" => false, "message" => "backup_data is required"]);
        exit;
    }

    $backupDataTrimmed = trim($backupData);
    $firstChar = substr($backupDataTrimmed, 0, 1);
    $lastChar = substr($backupDataTrimmed, -1);
    $isValidJsonStructure = (($firstChar === '{' && $lastChar === '}') || ($firstChar === '[' && $lastChar === ']'));
    if (!$isValidJsonStructure) {
        echo json_encode(["status" => false, "message" => "Invalid backup payload format: must be valid JSON"]);
        exit;
    }

    try {
        $backupSize = strlen($backupData);

        $checkStmt = $accountPdo->query("SELECT id FROM cloud_backups ORDER BY id DESC LIMIT 1");
        $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

        if ($existing) {
            $stmt = $accountPdo->prepare("UPDATE cloud_backups SET backup_name = ?, backup_data = ?, backup_size = ?, version = ?, created_at = CURRENT_TIMESTAMP WHERE id = ?");
            $stmt->execute([$backupName, $backupData, $backupSize, $version, $existing['id']]);
            $insertedId = $existing['id'];
            $msg = "Backup updated successfully on Hosting";
        } else {
            $stmt = $accountPdo->prepare("INSERT INTO cloud_backups (user_id, backup_name, backup_data, backup_size, version) VALUES (?, ?, ?, ?, ?)");
            $stmt->execute([$userId, $backupName, $backupData, $backupSize, $version]);
            $insertedId = $accountPdo->lastInsertId();
            $msg = "Backup created successfully on Hosting";
        }

        echo json_encode([
            "status" => true,
            "message" => $msg,
            "backup_id" => (string)$insertedId
        ]);
        exit;
    } catch (Throwable $e) {
        echo json_encode(["status" => false, "message" => "Database error creating/updating backup: " . $e->getMessage()]);
        exit;
    }
}

http_response_code(405);
echo json_encode(["status" => false, "message" => "Method not allowed"]);
