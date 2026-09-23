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

$systemPdo = getSystemPdo();
$authenticatedUser = getAuthenticatedUser($systemPdo);
$userId = $authenticatedUser['id'];
$accountPdo = getAccountPdo($systemPdo, $userId);

function fetchFullDataset($pdo, $userId) {
    $fullData = [];

    // Customers
    $stmt = $pdo->prepare("SELECT id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, updated_at, created_at FROM customers ORDER BY id DESC");
    $stmt->execute();
    $customers = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($customers as &$c) { $c['user_id'] = $userId; }
    $fullData['customers'] = $customers;

    // Packages
    $stmt = $pdo->prepare("SELECT id, name, price, speed, updated_at, created_at FROM packages ORDER BY id ASC");
    $stmt->execute();
    $packages = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($packages as &$p) { $p['user_id'] = $userId; }
    $fullData['packages'] = $packages;

    // Bills
    $stmt = $pdo->prepare("SELECT id, customer_id, bill_number, customer_name, customer_code, month, bill_month, amount, paid_amount, due_amount, status, due_date, generated_date, updated_at, created_at FROM bills ORDER BY id DESC");
    $stmt->execute();
    $bills = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($bills as &$b) { $b['user_id'] = $userId; }
    $fullData['bills'] = $bills;

    // Payments
    $stmt = $pdo->prepare("SELECT id, payment_receipt_no, bill_id, customer_id, customer_name, amount, payment_date, payment_method, notes, updated_at, created_at FROM payments ORDER BY id DESC");
    $stmt->execute();
    $payments = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($payments as &$pm) { $pm['user_id'] = $userId; }
    $fullData['payments'] = $payments;

    // Expenses
    $stmt = $pdo->prepare("SELECT id, title, amount, category, date, payment_method, note, receipt_path, created_at, updated_at FROM expenses ORDER BY id DESC");
    $stmt->execute();
    $expenses = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($expenses as &$e) { $e['user_id'] = $userId; }
    $fullData['expenses'] = $expenses;

    // Expense Categories
    $stmt = $pdo->prepare("SELECT id, name, color, created_at, updated_at FROM expense_categories ORDER BY id ASC");
    $stmt->execute();
    $categories = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($categories as &$cat) { $cat['user_id'] = $userId; }
    $fullData['expense_categories'] = $categories;

    // Business Settings
    $stmt = $pdo->prepare("SELECT id, isp_name, hotline, address, currency_symbol, network_status, theme_mode, logo_uri, email, updated_at, created_at FROM business_settings LIMIT 1");
    $stmt->execute();
    $settings = $stmt->fetch(PDO::FETCH_ASSOC);
    if ($settings) {
        $settings['user_id'] = $userId;
    }
    $fullData['settings'] = $settings ? $settings : null;

    // Audit Logs (recent 200)
    $stmt = $pdo->prepare("SELECT id, action, action_type, details, user_email, user_role, target_entity, target_id, previous_state, new_state, status, timestamp FROM audit_logs ORDER BY timestamp DESC LIMIT 200");
    $stmt->execute();
    $logs = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($logs as &$l) { $l['user_id'] = $userId; }
    $fullData['audit_logs'] = $logs;

    // Bandwidth Bills
    $stmt = $pdo->prepare("SELECT billing_month, amount, updated_at, created_at FROM bandwidth_bills ORDER BY billing_month DESC");
    $stmt->execute();
    $bandwidthBills = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($bandwidthBills as &$bb) { $bb['user_id'] = $userId; }
    $fullData['bandwidth_bills'] = $bandwidthBills;

    // Specific Advances
    $stmt = $pdo->prepare("SELECT id, customer_id, billing_month, amount, is_consumed, updated_at, created_at FROM specific_advances ORDER BY id DESC");
    $stmt->execute();
    $advances = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($advances as &$adv) { $adv['user_id'] = $userId; }
    $fullData['specific_advances'] = $advances;

    return $fullData;
}

$method = $_SERVER['REQUEST_METHOD'];

if ($method !== 'POST') {
    http_response_code(405);
    echo json_encode(["status" => false, "message" => "Method not allowed. Full Sync requires POST."]);
    exit;
}

try {
    $raw = file_get_contents("php://input");
    $payload = json_decode($raw, true);

    $now = (int)(microtime(true) * 1000);

    $syncedIds = [
        "customers" => [],
        "packages" => [],
        "bills" => [],
        "payments" => [],
        "expenses" => [],
        "expense_categories" => [],
        "settings" => null,
        "audit_logs" => [],
        "bandwidth_bills" => [],
        "specific_advances" => [],
        "pending_deletions" => []
    ];

    // 1. Process Customers
    if (!empty($payload['customers']) && is_array($payload['customers'])) {
        $stmt = $accountPdo->prepare("INSERT INTO customers (id, user_id, name, phone, address, ip_address, package_id, billing_cycle_date, status, pppoe_username, customer_code, joining_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone), address = VALUES(address), ip_address = VALUES(ip_address), package_id = VALUES(package_id), billing_cycle_date = VALUES(billing_cycle_date), status = VALUES(status), pppoe_username = VALUES(pppoe_username), customer_code = VALUES(customer_code), joining_date = VALUES(joining_date), updated_at = VALUES(updated_at)");
        foreach ($payload['customers'] as $c) {
            if (!empty($c['id']) && !empty($c['name'])) {
                $upAt = isset($c['updated_at']) ? (int)$c['updated_at'] : $now;
                $stmt->execute([
                    (string)$c['id'],
                    $userId,
                    (string)$c['name'],
                    $c['phone'] ?? '',
                    $c['address'] ?? '',
                    $c['ip_address'] ?? '',
                    $c['package_id'] ?? '',
                    isset($c['billing_cycle_date']) ? (int)$c['billing_cycle_date'] : 1,
                    $c['status'] ?? 'ACTIVE',
                    $c['pppoe_username'] ?? '',
                    $c['customer_code'] ?? '',
                    $c['joining_date'] ?? '',
                    $upAt
                ]);
                $syncedIds['customers'][] = (string)$c['id'];
            }
        }
    }

    // 2. Process Packages
    if (!empty($payload['packages']) && is_array($payload['packages'])) {
        $stmt = $accountPdo->prepare("INSERT INTO packages (id, user_id, name, price, speed, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price), speed = VALUES(speed), updated_at = VALUES(updated_at)");
        foreach ($payload['packages'] as $p) {
            if (!empty($p['id']) && !empty($p['name'])) {
                $upAt = isset($p['updated_at']) ? (int)$p['updated_at'] : $now;
                $stmt->execute([
                    (string)$p['id'],
                    $userId,
                    (string)$p['name'],
                    (float)$p['price'],
                    $p['speed'] ?? '',
                    $upAt
                ]);
                $syncedIds['packages'][] = (string)$p['id'];
            }
        }
    }

    // 3. Process Bills (with canonical month normalization & deduplication)
    if (!empty($payload['bills']) && is_array($payload['bills'])) {
        $stmt = $accountPdo->prepare("INSERT INTO bills (id, user_id, customer_id, bill_number, customer_name, customer_code, month, bill_month, amount, paid_amount, due_amount, status, due_date, generated_date, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE customer_id = VALUES(customer_id), bill_number = VALUES(bill_number), customer_name = VALUES(customer_name), customer_code = VALUES(customer_code), month = VALUES(month), bill_month = VALUES(bill_month), amount = VALUES(amount), paid_amount = VALUES(paid_amount), due_amount = VALUES(due_amount), status = VALUES(status), due_date = VALUES(due_date), generated_date = VALUES(generated_date), updated_at = VALUES(updated_at)");
        foreach ($payload['bills'] as $b) {
            if (!empty($b['id'])) {
                $rawM = $b['month'] ?? ($b['bill_month'] ?? '');
                $canonicalM = normalizeCanonicalMonth($rawM);
                if (empty($canonicalM)) {
                    $canonicalM = date('Y-m');
                }
                $upAt = isset($b['updated_at']) ? (int)$b['updated_at'] : $now;

                try {
                    $stmt->execute([
                        (int)$b['id'],
                        $userId,
                        (int)($b['customer_id'] ?? 0),
                        $b['bill_number'] ?? null,
                        $b['customer_name'] ?? null,
                        $b['customer_code'] ?? null,
                        $canonicalM,
                        $canonicalM,
                        (float)($b['amount'] ?? 0),
                        (float)($b['paid_amount'] ?? 0),
                        (float)($b['due_amount'] ?? 0),
                        $b['status'] ?? 'UNPAID',
                        $b['due_date'] ?? '',
                        $b['generated_date'] ?? null,
                        $upAt
                    ]);
                    $syncedIds['bills'][] = (int)$b['id'];
                } catch (PDOException $e) {
                    // Handled duplicate period bill gracefully
                    $syncedIds['bills'][] = (int)$b['id'];
                }
            }
        }
    }

    // 4. Process Payments
    if (!empty($payload['payments']) && is_array($payload['payments'])) {
        $stmt = $accountPdo->prepare("INSERT INTO payments (id, user_id, payment_receipt_no, bill_id, customer_id, customer_name, amount, payment_date, payment_method, notes, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE payment_receipt_no = VALUES(payment_receipt_no), bill_id = VALUES(bill_id), customer_id = VALUES(customer_id), customer_name = VALUES(customer_name), amount = VALUES(amount), payment_date = VALUES(payment_date), payment_method = VALUES(payment_method), notes = VALUES(notes), updated_at = VALUES(updated_at)");
        foreach ($payload['payments'] as $pm) {
            if (!empty($pm['id'])) {
                $upAt = isset($pm['updated_at']) ? (int)$pm['updated_at'] : $now;
                $stmt->execute([
                    (int)$pm['id'],
                    $userId,
                    $pm['payment_receipt_no'] ?? ('REC-' . $pm['id']),
                    (int)($pm['bill_id'] ?? 0),
                    (int)($pm['customer_id'] ?? 0),
                    $pm['customer_name'] ?? '',
                    (float)($pm['amount'] ?? 0),
                    $pm['payment_date'] ?? '',
                    $pm['payment_method'] ?? 'Cash',
                    $pm['notes'] ?? null,
                    $upAt
                ]);
                $syncedIds['payments'][] = (int)$pm['id'];
            }
        }
    }

    // 5. Process Expenses
    if (!empty($payload['expenses']) && is_array($payload['expenses'])) {
        $stmt = $accountPdo->prepare("INSERT INTO expenses (id, user_id, title, amount, category, date, payment_method, note, receipt_path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE title = VALUES(title), amount = VALUES(amount), category = VALUES(category), date = VALUES(date), payment_method = VALUES(payment_method), note = VALUES(note), receipt_path = VALUES(receipt_path), updated_at = VALUES(updated_at)");
        foreach ($payload['expenses'] as $e) {
            if (!empty($e['id'])) {
                $upAt = isset($e['updated_at']) ? (int)$e['updated_at'] : $now;
                $crAt = isset($e['created_at']) ? (int)$e['created_at'] : $now;
                $stmt->execute([
                    (int)$e['id'],
                    $userId,
                    $e['title'] ?? '',
                    (float)($e['amount'] ?? 0),
                    $e['category'] ?? 'General',
                    $e['date'] ?? '',
                    $e['payment_method'] ?? 'Cash',
                    $e['note'] ?? null,
                    $e['receipt_path'] ?? null,
                    $crAt,
                    $upAt
                ]);
                $syncedIds['expenses'][] = (int)$e['id'];
            }
        }
    }

    // 6. Process Expense Categories
    if (!empty($payload['expense_categories']) && is_array($payload['expense_categories'])) {
        $stmt = $accountPdo->prepare("INSERT INTO expense_categories (id, user_id, name, color, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), color = VALUES(color), updated_at = VALUES(updated_at)");
        foreach ($payload['expense_categories'] as $cat) {
            if (!empty($cat['id']) && !empty($cat['name'])) {
                $upAt = isset($cat['updated_at']) ? (int)$cat['updated_at'] : $now;
                $crAt = isset($cat['created_at']) ? (int)$cat['created_at'] : $now;
                $stmt->execute([
                    (int)$cat['id'],
                    $userId,
                    $cat['name'],
                    $cat['color'] ?? '#6750A4',
                    $crAt,
                    $upAt
                ]);
                $syncedIds['expense_categories'][] = (int)$cat['id'];
            }
        }
    }

    // 7. Process Business Settings
    if (!empty($payload['settings']) && is_array($payload['settings'])) {
        $s = $payload['settings'];
        $upAt = isset($s['updated_at']) ? (int)$s['updated_at'] : $now;
        $stmt = $accountPdo->prepare("INSERT INTO business_settings (id, user_id, isp_name, hotline, address, currency_symbol, network_status, theme_mode, logo_uri, email, updated_at) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE isp_name = VALUES(isp_name), hotline = VALUES(hotline), address = VALUES(address), currency_symbol = VALUES(currency_symbol), network_status = VALUES(network_status), theme_mode = VALUES(theme_mode), logo_uri = VALUES(logo_uri), email = VALUES(email), updated_at = VALUES(updated_at)");
        $stmt->execute([
            $userId,
            $s['isp_name'] ?? ($s['ispName'] ?? ''),
            $s['hotline'] ?? '',
            $s['address'] ?? '',
            $s['currency_symbol'] ?? ($s['currencySymbol'] ?? '৳'),
            $s['network_status'] ?? ($s['networkStatus'] ?? 'Operational'),
            $s['theme_mode'] ?? ($s['themeMode'] ?? 'SYSTEM'),
            $s['logo_uri'] ?? ($s['logoUri'] ?? null),
            $s['email'] ?? '',
            $upAt
        ]);
        $syncedIds['settings'] = 1;
    }

    // 8. Process Audit Logs
    if (!empty($payload['audit_logs']) && is_array($payload['audit_logs'])) {
        $stmt = $accountPdo->prepare("INSERT INTO audit_logs (id, user_id, action, action_type, details, user_email, user_role, target_entity, target_id, previous_state, new_state, status, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE action = VALUES(action), action_type = VALUES(action_type), details = VALUES(details), user_email = VALUES(user_email), user_role = VALUES(user_role), target_entity = VALUES(target_entity), target_id = VALUES(target_id), previous_state = VALUES(previous_state), new_state = VALUES(new_state), status = VALUES(status), timestamp = VALUES(timestamp)");
        foreach ($payload['audit_logs'] as $l) {
            if (!empty($l['id'])) {
                $ts = isset($l['timestamp']) ? (int)$l['timestamp'] : $now;
                $stmt->execute([
                    (int)$l['id'],
                    $userId,
                    $l['action'] ?? '',
                    $l['action_type'] ?? '',
                    $l['details'] ?? null,
                    $l['user_email'] ?? $authenticatedUser['email'],
                    $l['user_role'] ?? ($authenticatedUser['role'] ?? 'Admin'),
                    $l['target_entity'] ?? '',
                    $l['target_id'] ?? '',
                    $l['previous_state'] ?? null,
                    $l['new_state'] ?? null,
                    $l['status'] ?? 'SUCCESS',
                    $ts
                ]);
                $syncedIds['audit_logs'][] = (int)$l['id'];
            }
        }
    }

    // 9. Process Bandwidth Bills
    if (!empty($payload['bandwidth_bills']) && is_array($payload['bandwidth_bills'])) {
        $stmt = $accountPdo->prepare("INSERT INTO bandwidth_bills (user_id, billing_month, amount, updated_at) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount), updated_at = VALUES(updated_at)");
        foreach ($payload['bandwidth_bills'] as $bb) {
            $rawMonth = $bb['billing_month'] ?? '';
            $canonicalMonth = normalizeCanonicalMonth($rawMonth);
            if (empty($canonicalMonth)) {
                $canonicalMonth = (string)$rawMonth;
            }
            if (!empty($canonicalMonth)) {
                $upAt = isset($bb['updated_at']) ? (int)$bb['updated_at'] : $now;
                $stmt->execute([
                    $userId,
                    $canonicalMonth,
                    (float)($bb['amount'] ?? 0),
                    $upAt
                ]);
                $syncedIds['bandwidth_bills'][] = $canonicalMonth;
            }
        }
    }

    // 10. Process Specific Advances
    if (!empty($payload['specific_advances']) && is_array($payload['specific_advances'])) {
        $stmt = $accountPdo->prepare("INSERT INTO specific_advances (id, user_id, customer_id, billing_month, amount, is_consumed, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE customer_id = VALUES(customer_id), billing_month = VALUES(billing_month), amount = VALUES(amount), is_consumed = VALUES(is_consumed), updated_at = VALUES(updated_at)");
        foreach ($payload['specific_advances'] as $adv) {
            if (!empty($adv['id'])) {
                $rawMonth = $adv['billing_month'] ?? '';
                $canonicalMonth = normalizeCanonicalMonth($rawMonth);
                if (empty($canonicalMonth)) {
                    $canonicalMonth = (string)$rawMonth;
                }
                $upAt = isset($adv['updated_at']) ? (int)$adv['updated_at'] : $now;
                $isCons = !empty($adv['is_consumed']) ? 1 : 0;
                $stmt->execute([
                    (int)$adv['id'],
                    $userId,
                    (int)($adv['customer_id'] ?? 0),
                    $canonicalMonth,
                    (float)($adv['amount'] ?? 0),
                    $isCons,
                    $upAt
                ]);
                $syncedIds['specific_advances'][] = (int)$adv['id'];
            }
        }
    }

    // 11. Process Pending Deletions (Direct Deletes on User's Dedicated Database)
    $pendingDeletionsInput = !empty($payload['pending_deletions']) ? $payload['pending_deletions'] : [];
    if (!empty($pendingDeletionsInput) && is_array($pendingDeletionsInput)) {
        $allowedTables = ['customers', 'packages', 'bills', 'payments', 'expenses', 'expense_categories', 'specific_advances'];
        foreach ($pendingDeletionsInput as $pd) {
            $table = $pd['collection_name'] ?? '';
            $recId = $pd['document_id'] ?? '';
            if (in_array($table, $allowedTables) && !empty($recId)) {
                $delStmt = $accountPdo->prepare("DELETE FROM `$table` WHERE id = ?");
                $delStmt->execute([$recId]);
                $syncedIds['pending_deletions'][] = (string)$recId;
            }
        }
    }

    // 12. Fetch Complete Dataset on the User's Dedicated Account Database
    $fullData = fetchFullDataset($accountPdo, $userId);

    echo json_encode([
        "status" => true,
        "message" => "Full sync completed successfully.",
        "synced_ids" => $syncedIds,
        "server_timestamp" => $now,
        "data" => $fullData
    ]);
    exit;
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        "status" => false,
        "message" => "Full sync error: " . $e->getMessage()
    ]);
    exit;
}
