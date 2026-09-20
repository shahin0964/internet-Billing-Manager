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
            'status' => "VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'"
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

$email = isset($data['email']) ? trim($data['email']) : '';
$password = isset($data['password']) ? (string)$data['password'] : '';

if (empty($email) || empty($password)) {
    echo json_encode([
        "status" => false,
        "message" => "Email and password are required.",
        "user" => null
    ]);
    exit;
}

ensureUsersSchema($pdo);

$stmt = $pdo->prepare("SELECT id, name, email, password_hash, role, status FROM users WHERE email = ? LIMIT 1");
$stmt->execute([$email]);
$user = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$user) {
    echo json_encode([
        "status" => false,
        "message" => "No account found with this email address.",
        "user" => null
    ]);
    exit;
}

if (!password_verify($password, $user['password_hash'])) {
    echo json_encode([
        "status" => false,
        "message" => "Incorrect password. Please try again.",
        "user" => null
    ]);
    exit;
}

echo json_encode([
    "status" => true,
    "message" => "Login successful!",
    "user" => [
        "id" => (string)$user['id'],
        "name" => (string)$user['name'],
        "email" => (string)$user['email']
    ]
]);
exit;
