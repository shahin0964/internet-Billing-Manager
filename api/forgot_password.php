<?php
ob_start();
error_reporting(0);
ini_set('display_errors', '0');

header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once 'db.php';

function ensurePasswordResetsSchema($pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS password_resets (
            id INT AUTO_INCREMENT PRIMARY KEY,
            email VARCHAR(255) NOT NULL,
            code VARCHAR(10) NOT NULL,
            token VARCHAR(128) NULL,
            expires_at DATETIME NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_email (email),
            INDEX idx_code (code),
            INDEX idx_token (token)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {
        // Table creation fallback
    }
}

function sendVerificationEmail($recipientEmail, $code) {
    $serverName = $_SERVER['SERVER_NAME'] ?? 'ispbillingmanagement.dev.cv';
    if (empty($serverName) || $serverName === 'localhost') {
        $serverName = 'ispbillingmanagement.dev.cv';
    }
    
    $fromEmail = "noreply@" . preg_replace('/^www\./', '', $serverName);
    $subject = "ISP Billing - Password Reset Verification Code";

    $message = '<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Password Reset Verification Code</title>
</head>
<body style="font-family: Arial, sans-serif; background-color: #f4f7f9; margin: 0; padding: 24px; color: #333333;">
    <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 560px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.08);">
        <tr>
            <td style="background-color: #1976D2; padding: 28px; text-align: center; color: #ffffff;">
                <h1 style="margin: 0; font-size: 24px; font-weight: bold;">ISP Billing Management</h1>
                <p style="margin: 6px 0 0 0; font-size: 14px; opacity: 0.9;">Password Reset Request</p>
            </td>
        </tr>
        <tr>
            <td style="padding: 32px 28px;">
                <p style="font-size: 16px; margin: 0 0 16px 0; line-height: 1.5;">Hello,</p>
                <p style="font-size: 15px; margin: 0 0 24px 0; line-height: 1.5; color: #555555;">
                    We received a request to reset your password for your ISP Billing account. Use the verification code below to complete the reset process:
                </p>
                <div style="text-align: center; margin: 28px 0;">
                    <div style="display: inline-block; background-color: #f0f4f8; border: 2px dashed #1976D2; border-radius: 8px; padding: 14px 28px; font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #1976D2;">
                        ' . htmlspecialchars($code, ENT_QUOTES, 'UTF-8') . '
                    </div>
                </div>
                <p style="font-size: 14px; margin: 0 0 12px 0; line-height: 1.5; color: #666666; text-align: center;">
                    This verification code is valid for <strong>15 minutes</strong>.
                </p>
                <p style="font-size: 13px; margin: 24px 0 0 0; line-height: 1.5; color: #888888; border-top: 1px solid #eeeeee; padding-top: 16px;">
                    If you did not request this password reset, please ignore this email or contact support if you suspect unauthorized activity.
                </p>
            </td>
        </tr>
        <tr>
            <td style="background-color: #fafafa; padding: 16px 28px; text-align: center; font-size: 12px; color: #999999; border-top: 1px solid #eeeeee;">
                &copy; ' . date('Y') . ' ISP Billing Management System. All rights reserved.
            </td>
        </tr>
    </table>
</body>
</html>';

    $headers = [];
    $headers[] = "MIME-Version: 1.0";
    $headers[] = "Content-type: text/html; charset=UTF-8";
    $headers[] = "From: ISP Billing System <" . $fromEmail . ">";
    $headers[] = "Reply-To: " . $fromEmail;
    $headers[] = "X-Mailer: PHP/" . phpversion();

    $mailSent = @mail($recipientEmail, $subject, $message, implode("\r\n", $headers));
    return $mailSent;
}

try {
    $systemPdo = getSystemPdo();
    ensureSystemUsersSchema($systemPdo);
    ensurePasswordResetsSchema($systemPdo);

    $raw = file_get_contents("php://input");
    $data = json_decode($raw, true) ?: [];

    $action = $_GET['action'] ?? ($data['action'] ?? 'send_code');
    $email = trim($data['email'] ?? ($_GET['email'] ?? ''));

    if (empty($email)) {
        ob_clean();
        echo json_encode([
            "status" => false,
            "message" => "Email address is required."
        ]);
        exit;
    }

    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        ob_clean();
        echo json_encode([
            "status" => false,
            "message" => "Please enter a valid Gmail / Email address."
        ]);
        exit;
    }

    // Step 1: Send Verification Code
    if ($action === 'send_code' || $action === 'request_otp' || $action === 'forgot') {
        // Verify user exists
        $userStmt = $systemPdo->prepare("SELECT id, name, email FROM users WHERE email = ? LIMIT 1");
        $userStmt->execute([$email]);
        $user = $userStmt->fetch(PDO::FETCH_ASSOC);

        if (!$user) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "No account found with this email address. Please check your email."
            ]);
            exit;
        }

        // Generate 6-digit numeric verification code
        $code = sprintf("%06d", random_int(100000, 999999));
        $token = bin2hex(random_bytes(24));
        $expiresAt = date("Y-m-d H:i:s", time() + 900); // 15 minutes validity

        // Clean previous reset attempts for this email
        $delStmt = $systemPdo->prepare("DELETE FROM password_resets WHERE email = ?");
        $delStmt->execute([$email]);

        // Insert new reset entry
        $insStmt = $systemPdo->prepare("INSERT INTO password_resets (email, code, token, expires_at) VALUES (?, ?, ?, ?)");
        $insStmt->execute([$email, $code, $token, $expiresAt]);

        // Dispatch real email
        sendVerificationEmail($email, $code);

        ob_clean();
        echo json_encode([
            "status" => true,
            "message" => "Verification code sent to " . $email . ". Please check your inbox (and spam folder).",
            "reset_token" => $token
        ]);
        exit;
    }

    // Step 2: Verify Code
    if ($action === 'verify_code' || $action === 'verify_otp') {
        $code = trim($data['code'] ?? ($data['otp'] ?? ($_GET['code'] ?? ($_GET['otp'] ?? ''))));
        if (empty($code)) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "Please enter the 6-digit verification code."
            ]);
            exit;
        }

        $stmt = $systemPdo->prepare("SELECT id, email, token, expires_at FROM password_resets WHERE email = ? AND code = ? LIMIT 1");
        $stmt->execute([$email, $code]);
        $resetRecord = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$resetRecord) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "Invalid verification code. Please check and try again."
            ]);
            exit;
        }

        if (strtotime($resetRecord['expires_at']) < time()) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "Verification code has expired. Please request a new code."
            ]);
            exit;
        }

        $token = $resetRecord['token'];
        if (empty($token)) {
            $token = bin2hex(random_bytes(24));
            $upStmt = $systemPdo->prepare("UPDATE password_resets SET token = ? WHERE id = ?");
            $upStmt->execute([$token, $resetRecord['id']]);
        }

        ob_clean();
        echo json_encode([
            "status" => true,
            "message" => "Verification code verified successfully!",
            "reset_token" => $token
        ]);
        exit;
    }

    // Step 3: Reset Password
    if ($action === 'reset_password' || $action === 'verify_and_reset' || $action === 'update_password') {
        $newPassword = (string)($data['new_password'] ?? ($data['password'] ?? ''));
        $code = trim($data['code'] ?? ($data['otp'] ?? ''));
        $resetToken = trim($data['reset_token'] ?? '');

        if (empty($newPassword)) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "New password cannot be empty."
            ]);
            exit;
        }

        if (strlen($newPassword) < 6) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "Password must be at least 6 characters long."
            ]);
            exit;
        }

        // Validate code or reset token
        $verified = false;
        if (!empty($resetToken)) {
            $stmt = $systemPdo->prepare("SELECT id FROM password_resets WHERE email = ? AND token = ? AND expires_at >= NOW() LIMIT 1");
            $stmt->execute([$email, $resetToken]);
            if ($stmt->fetch()) {
                $verified = true;
            }
        }

        if (!$verified && !empty($code)) {
            $stmt = $systemPdo->prepare("SELECT id FROM password_resets WHERE email = ? AND code = ? AND expires_at >= NOW() LIMIT 1");
            $stmt->execute([$email, $code]);
            if ($stmt->fetch()) {
                $verified = true;
            }
        }

        if (!$verified) {
            ob_clean();
            echo json_encode([
                "status" => false,
                "message" => "Invalid or expired session. Please start the password reset process again."
            ]);
            exit;
        }

        // Hash new password using bcrypt
        $passwordHash = password_hash($newPassword, PASSWORD_BCRYPT);

        // Update password in central users table
        $updateStmt = $systemPdo->prepare("UPDATE users SET password_hash = ? WHERE email = ?");
        $updateStmt->execute([$passwordHash, $email]);

        // Invalidate all tokens for this email
        $cleanStmt = $systemPdo->prepare("DELETE FROM password_resets WHERE email = ?");
        $cleanStmt->execute([$email]);

        ob_clean();
        echo json_encode([
            "status" => true,
            "message" => "Password updated successfully! You can now log in with your new password."
        ]);
        exit;
    }

    ob_clean();
    echo json_encode([
        "status" => false,
        "message" => "Invalid action specified."
    ]);
    exit;

} catch (Throwable $e) {
    ob_clean();
    echo json_encode([
        "status" => false,
        "message" => "An error occurred while processing your request. Please try again."
    ]);
    exit;
}
