<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once 'db.php';

function ensureUsersSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS users (
            id VARCHAR(100) NOT NULL,
            name VARCHAR(255) NOT NULL DEFAULT '',
            email VARCHAR(255) NOT NULL,
            password_hash VARCHAR(255) NOT NULL,
            phone VARCHAR(50) NULL DEFAULT '',
            role VARCHAR(50) NOT NULL DEFAULT 'User',
            status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            UNIQUE KEY uk_email (email),
            INDEX idx_email (email)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $stmt = $pdo->query("SHOW COLUMNS FROM users");
        $existingCols = $stmt->fetchAll(PDO::FETCH_COLUMN);

        $colsToAdd = [
            'name' => "VARCHAR(255) NOT NULL DEFAULT ''",
            'email' => "VARCHAR(255) NOT NULL",
            'password_hash' => "VARCHAR(255) NOT NULL",
            'phone' => "VARCHAR(50) NULL DEFAULT ''",
            'role' => "VARCHAR(50) NOT NULL DEFAULT 'User'",
            'status' => "VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'",
            'api_token' => "VARCHAR(128) NULL UNIQUE"
        ];

        foreach ($colsToAdd as $col => $definition) {
            if (!in_array($col, $existingCols)) {
                $pdo->exec("ALTER TABLE users ADD COLUMN $col $definition");
            }
        }
    } catch (Exception $e) {
        // Continue safely
    }
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(["status" => false, "message" => "Method not allowed", "user" => null]);
    exit;
}

$raw = file_get_contents("php://input");
$data = json_decode($raw, true);

$name = isset($data['name']) ? trim($data['name']) : '';
$email = isset($data['email']) ? trim($data['email']) : '';
$password = isset($data['password']) ? (string)$data['password'] : '';
$phone = isset($data['phone']) ? trim($data['phone']) : '';

if (empty($name) || empty($email) || empty($password)) {
    echo json_encode([
        "status" => false,
        "message" => "Name, email, and password are required.",
        "user" => null
    ]);
    exit;
}

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    echo json_encode([
        "status" => false,
        "message" => "Please enter a valid email address.",
        "user" => null
    ]);
    exit;
}

ensureUsersSchema($pdo);

// Check if email already exists
$checkStmt = $pdo->prepare("SELECT id FROM users WHERE email = ? LIMIT 1");
$checkStmt->execute([$email]);
if ($checkStmt->fetch()) {
    echo json_encode([
        "status" => false,
        "message" => "An account with this email address already exists.",
        "user" => null
    ]);
    exit;
}

// Generate unique user ID
$userId = "usr_" . bin2hex(random_bytes(8));

// Check if this is the first user (make Admin)
$countStmt = $pdo->query("SELECT COUNT(*) FROM users");
$userCount = (int)$countStmt->fetchColumn();
$role = ($userCount === 0) ? 'Admin' : 'User';

// Hash password with bcrypt
$passwordHash = password_hash($password, PASSWORD_BCRYPT);

$apiToken = bin2hex(random_bytes(32));

$insertStmt = $pdo->prepare("INSERT INTO users (id, name, email, password_hash, phone, role, status, api_token) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)");
$insertStmt->execute([$userId, $name, $email, $passwordHash, $phone, $role, $apiToken]);

echo json_encode([
    "status" => true,
    "message" => "Account created successfully!",
    "user" => [
        "id" => $userId,
        "name" => $name,
        "email" => $email,
        "api_token" => $apiToken
    ]
]);
exit;
