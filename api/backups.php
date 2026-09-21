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

function ensureBackupsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        // Use a highly compatible schema that works on all MySQL/MariaDB versions (no dual DEFAULT CURRENT_TIMESTAMP limitations)
        $pdo->exec("CREATE TABLE IF NOT EXISTS cloud_backups (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id VARCHAR(100) NOT NULL,
            backup_name VARCHAR(255) NOT NULL DEFAULT '',
            backup_data LONGTEXT NOT NULL,
            backup_size INT NOT NULL DEFAULT 0,
            version INT NOT NULL DEFAULT 1,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_user_id (user_id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {
        // Continue safely
    }
}

$method = $_SERVER['REQUEST_METHOD'];

if ($method === 'GET') {
    $userId = $_GET['user_id'] ?? null;
    if (!$userId) {
        echo json_encode(["status" => false, "message" => "user_id is required", "data" => null]);
        exit;
    }

    try {
        ensureBackupsSchema($pdo);

        $action = $_GET['action'] ?? 'latest';

        if ($action === 'latest') {
            $stmt = $pdo->prepare("SELECT id, user_id, backup_name, backup_data, backup_size, version, UNIX_TIMESTAMP(created_at) * 1000 AS created_at FROM cloud_backups WHERE user_id = ? ORDER BY created_at DESC LIMIT 1");
            $stmt->execute([$userId]);
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
                    "user_id" => $backup['user_id'],
                    "backup_name" => $backup['backup_name'],
                    "backup_data" => $backup['backup_data'],
                    "backup_size" => (int)$backup['backup_size'],
                    "version" => (int)$backup['version'],
                    "created_at" => (int)$backup['created_at']
                ]
            ]);
            exit;
        } elseif ($action === 'list') {
            $stmt = $pdo->prepare("SELECT id, user_id, backup_name, backup_size, version, UNIX_TIMESTAMP(created_at) * 1000 AS created_at FROM cloud_backups WHERE user_id = ? ORDER BY created_at DESC LIMIT 10");
            $stmt->execute([$userId]);
            $backups = $stmt->fetchAll(PDO::FETCH_ASSOC);

            $formatted = array_map(function($b) {
                return [
                    "id" => (string)$b['id'],
                    "user_id" => $b['user_id'],
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

    $userId = $data['user_id'] ?? null;
    $backupData = $data['backup_data'] ?? null;
    $backupName = $data['backup_name'] ?? ('ISP-Cloud-Backup-' . date('Y-m-d-H-i-s'));
    $version = isset($data['version']) ? (int)$data['version'] : 1;

    if (!$userId || !$backupData) {
        echo json_encode(["status" => false, "message" => "user_id and backup_data are required"]);
        exit;
    }

    // Validate backup data has valid JSON structure without fully decoding it in memory
    $backupDataTrimmed = trim($backupData);
    $firstChar = substr($backupDataTrimmed, 0, 1);
    $lastChar = substr($backupDataTrimmed, -1);
    $isValidJsonStructure = (($firstChar === '{' && $lastChar === '}') || ($firstChar === '[' && $lastChar === ']'));
    if (!$isValidJsonStructure) {
        echo json_encode(["status" => false, "message" => "Invalid backup payload format: must be valid JSON"]);
        exit;
    }

    try {
        ensureBackupsSchema($pdo);

        $backupSize = strlen($backupData);

        // Check if an existing backup already exists for this user
        $checkStmt = $pdo->prepare("SELECT id FROM cloud_backups WHERE user_id = ? LIMIT 1");
        $checkStmt->execute([$userId]);
        $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

        if ($existing) {
            // Update existing record and overwrite
            $stmt = $pdo->prepare("UPDATE cloud_backups SET backup_name = ?, backup_data = ?, backup_size = ?, version = ?, created_at = CURRENT_TIMESTAMP WHERE user_id = ?");
            $stmt->execute([$backupName, $backupData, $backupSize, $version, $userId]);
            $insertedId = $existing['id'];
            $msg = "Backup updated successfully on Hosting";
        } else {
            // Insert first record
            $stmt = $pdo->prepare("INSERT INTO cloud_backups (user_id, backup_name, backup_data, backup_size, version) VALUES (?, ?, ?, ?, ?)");
            $stmt->execute([$userId, $backupName, $backupData, $backupSize, $version]);
            $insertedId = $pdo->lastInsertId();
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
