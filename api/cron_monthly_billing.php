<?php
/**
 * ISP Billing Manager - Autonomous Server-Side Monthly Billing Scheduler
 *
 * This script is designed to run via cPanel Cron or PHP CLI on the 1st of every month at 00:00:00.
 *
 * Cron Configuration Example:
 * 0 0 1 * * /usr/local/bin/php /path/to/api/cron_monthly_billing.php >> /path/to/logs/cron_billing.log 2>&1
 *
 * Execution Flow:
 * 1. CLI-only security check (rejects unauthenticated HTTP requests).
 * 2. Non-blocking file lock to prevent overlapping cron jobs.
 * 3. Sets business timezone to 'Asia/Dhaka'.
 * 4. Connects to System DB and iterates all active users.
 * 5. Resolves each user's isolated account database (isp_acc_<userId>).
 * 6. Iterates customers, filters active & non-free lines, verifies existence of current-month bills.
 * 7. Applies specific advances (if available), sets due amounts, and creates exactly one BillEntity per customer.
 * 8. Protects against duplicates via database unique constraint (uq_customer_billing_month).
 * 9. Records audit log in tenant account database.
 * 10. Outputs structured execution summary.
 */

// 1. Strict CLI Security Check
if (php_sapi_name() !== 'cli' && empty($_SERVER['argv'])) {
    http_response_code(403);
    header('Content-Type: application/json; charset=UTF-8');
    echo json_encode([
        'status' => false,
        'message' => 'Forbidden: This scheduler can only be executed via PHP CLI / Cron.'
    ]);
    exit(1);
}

// 2. Overlapping Execution Protection (File Lock)
$lockFile = sys_get_temp_dir() . '/isp_cron_monthly_billing.lock';
$lockHandle = fopen($lockFile, 'c+');
if (!$lockHandle || !flock($lockHandle, LOCK_EX | LOCK_NB)) {
    echo "[" . date('Y-m-d H:i:s') . "] Another instance of the monthly billing scheduler is already running. Exiting.\n";
    exit(0);
}

// 3. Explicit Business Timezone
date_default_timezone_set('Asia/Dhaka');

require_once __DIR__ . '/db.php';

$canonicalMonth = date('Y-m');
$displayMonth = date('F Y');
$dueDate = date('Y-m-10');
$generatedDate = date('Y-m-d');
$executionStart = microtime(true);

echo "===============================================================\n";
echo " ISP BILLING MANAGER - SERVER-SIDE MONTHLY BILLING SCHEDULER\n";
echo "===============================================================\n";
echo "Execution Time : " . date('Y-m-d H:i:s T') . "\n";
echo "Billing Month  : $canonicalMonth ($displayMonth)\n";
echo "Standard Due   : $dueDate\n";
echo "---------------------------------------------------------------\n";

$summary = [
    'users_total' => 0,
    'users_processed' => 0,
    'users_failed' => 0,
    'customers_checked' => 0,
    'customers_eligible' => 0,
    'customers_skipped_inactive_suspended' => 0,
    'customers_skipped_free_package' => 0,
    'bills_created' => 0,
    'bills_already_existed' => 0,
    'bills_duplicate_race_skipped' => 0,
    'errors' => []
];

try {
    $systemPdo = getSystemPdo();
    $userStmt = $systemPdo->query("SELECT id, name, email, status, db_name FROM users");
    $users = $userStmt->fetchAll(PDO::FETCH_ASSOC);
    $summary['users_total'] = count($users);

    foreach ($users as $user) {
        $userId = $user['id'];
        $userStatus = strtoupper(trim($user['status'] ?? 'ACTIVE'));

        if ($userStatus !== 'ACTIVE') {
            continue;
        }

        $summary['users_processed']++;
        $userBillsCreated = 0;

        try {
            // Resolve dedicated physical account database
            $accountPdo = getAccountPdo($systemPdo, $userId);
            ensureAccountDatabaseTables($accountPdo);

            // Fetch package lookup map
            $pkgStmt = $accountPdo->query("SELECT id, name, price, speed FROM packages");
            $packages = [];
            while ($p = $pkgStmt->fetch(PDO::FETCH_ASSOC)) {
                $packages[(string)$p['id']] = $p;
            }

            // Fetch customer list for this tenant
            $custStmt = $accountPdo->query("SELECT id, name, phone, address, ip_address, package_id, status, pppoe_username, customer_code FROM customers");
            $customers = $custStmt->fetchAll(PDO::FETCH_ASSOC);

            foreach ($customers as $customer) {
                $summary['customers_checked']++;
                $custId = $customer['id'];
                $custStatus = strtoupper(trim($customer['status'] ?? ''));

                // Strict Customer Eligibility Rules:
                // ACTIVE -> eligible
                // SUSPENDED, INACTIVE, EXPIRED -> skipped
                $isInactiveOrSuspended = ($custStatus === 'INACTIVE' ||
                    $custStatus === 'SUSPENDED' ||
                    $custStatus === 'EXPIRED' ||
                    strpos($custStatus, 'SUSPEND') !== false ||
                    strpos($custStatus, 'INACT') !== false ||
                    $custStatus !== 'ACTIVE');

                if ($isInactiveOrSuspended) {
                    $summary['customers_skipped_inactive_suspended']++;
                    continue;
                }

                // Resolve package & monthly fee
                $pkgId = (string)($customer['package_id'] ?? '');
                $matchedPkg = $packages[$pkgId] ?? null;
                $packageName = $matchedPkg['name'] ?? '';

                // Free Package Safeguard
                $isFreePackage = (stripos($packageName, 'free') !== false ||
                    stripos($packageName, 'ফ্রি') !== false);

                if ($isFreePackage) {
                    $summary['customers_skipped_free_package']++;
                    continue;
                }

                $monthlyFee = isset($matchedPkg['price']) ? (float)$matchedPkg['price'] : 0.0;
                if ($monthlyFee <= 0.0) {
                    $summary['customers_skipped_free_package']++;
                    continue;
                }

                $summary['customers_eligible']++;

                // Application-level duplicate check for canonical month
                $dupStmt = $accountPdo->prepare("SELECT id, status, amount, paid_amount, due_amount FROM bills WHERE customer_id = ? AND (month = ? OR bill_month = ? OR month = ? OR bill_month = ?) LIMIT 1");
                $dupStmt->execute([$custId, $canonicalMonth, $canonicalMonth, $displayMonth, $displayMonth]);
                $existingBill = $dupStmt->fetch(PDO::FETCH_ASSOC);

                if ($existingBill) {
                    $summary['bills_already_existed']++;
                    continue;
                }

                // Customer Transaction Boundary
                try {
                    $accountPdo->beginTransaction();

                    // Re-check with row lock inside transaction
                    $lockCheck = $accountPdo->prepare("SELECT id FROM bills WHERE customer_id = ? AND (month = ? OR bill_month = ?) LIMIT 1 FOR UPDATE");
                    $lockCheck->execute([$custId, $canonicalMonth, $canonicalMonth]);
                    if ($lockCheck->fetch()) {
                        $accountPdo->rollBack();
                        $summary['bills_already_existed']++;
                        continue;
                    }

                    // Check for unconsumed specific advance for this month
                    $advStmt = $accountPdo->prepare("SELECT id, amount, is_consumed FROM specific_advances WHERE customer_id = ? AND (billing_month = ? OR billing_month = ?) AND is_consumed = 0 LIMIT 1 FOR UPDATE");
                    $advStmt->execute([$custId, $canonicalMonth, $displayMonth]);
                    $specificAdvance = $advStmt->fetch(PDO::FETCH_ASSOC);

                    $billAmount = $monthlyFee;
                    $paidAmount = 0.0;
                    $dueAmount = $monthlyFee;
                    $billStatus = 'UNPAID';
                    $now = (int)(microtime(true) * 1000);

                    if ($specificAdvance && (float)$specificAdvance['amount'] > 0.0) {
                        $advAmt = (float)$specificAdvance['amount'];
                        if ($advAmt >= $monthlyFee) {
                            $paidAmount = $monthlyFee;
                            $dueAmount = 0.0;
                            $billStatus = 'PAID';
                            $remainingAdv = $advAmt - $monthlyFee;
                            if ($remainingAdv > 0.0) {
                                $updAdv = $accountPdo->prepare("UPDATE specific_advances SET amount = ?, updated_at = ? WHERE id = ?");
                                $updAdv->execute([$remainingAdv, $now, $specificAdvance['id']]);
                            } else {
                                $updAdv = $accountPdo->prepare("UPDATE specific_advances SET is_consumed = 1, updated_at = ? WHERE id = ?");
                                $updAdv->execute([$now, $specificAdvance['id']]);
                            }
                        } else {
                            $paidAmount = $advAmt;
                            $dueAmount = max(0.0, $monthlyFee - $advAmt);
                            $billStatus = ($dueAmount <= 0.0 ? 'PAID' : ($paidAmount > 0.0 ? 'PARTIAL' : 'UNPAID'));
                            $updAdv = $accountPdo->prepare("UPDATE specific_advances SET is_consumed = 1, updated_at = ? WHERE id = ?");
                            $updAdv->execute([$now, $specificAdvance['id']]);
                        }
                    }

                    $billId = (int)(microtime(true) * 1000) + mt_rand(100, 999);
                    $billNo = "BILL-" . substr((string)$now, -6) . "-" . $custId;

                    $insertStmt = $accountPdo->prepare("INSERT INTO bills (
                        id, user_id, customer_id, bill_number, customer_name, customer_code,
                        month, bill_month, amount, paid_amount, due_amount, status,
                        due_date, generated_date, updated_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");

                    $insertStmt->execute([
                        $billId,
                        $userId,
                        $custId,
                        $billNo,
                        $customer['name'] ?? '',
                        $customer['customer_code'] ?? '',
                        $canonicalMonth,
                        $canonicalMonth,
                        $billAmount,
                        $paidAmount,
                        $dueAmount,
                        $billStatus,
                        $dueDate,
                        $generatedDate,
                        $now
                    ]);

                    $accountPdo->commit();
                    $userBillsCreated++;
                    $summary['bills_created']++;

                } catch (PDOException $pe) {
                    if ($accountPdo->inTransaction()) {
                        $accountPdo->rollBack();
                    }
                    if ($pe->getCode() == 23000 || strpos($pe->getMessage(), 'Duplicate entry') !== false) {
                        $summary['bills_duplicate_race_skipped']++;
                    } else {
                        $summary['errors'][] = "User $userId Customer $custId DB Error: " . $pe->getMessage();
                    }
                } catch (Throwable $ce) {
                    if ($accountPdo->inTransaction()) {
                        $accountPdo->rollBack();
                    }
                    $summary['errors'][] = "User $userId Customer $custId Error: " . $ce->getMessage();
                }
            }

            // Write Tenant Audit Log if new bills were created
            if ($userBillsCreated > 0) {
                try {
                    $auditId = (int)(microtime(true) * 1000) + mt_rand(100, 999);
                    $logStmt = $accountPdo->prepare("INSERT INTO audit_logs (
                        id, user_id, action, action_type, details, user_email, user_role,
                        target_entity, target_id, status, timestamp, created_timestamp
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
                    $logStmt->execute([
                        $auditId,
                        $userId,
                        "Automated Monthly Billing: Generated $userBillsCreated bills for $canonicalMonth",
                        "CRON_BILLING",
                        json_encode([
                            'billing_month' => $canonicalMonth,
                            'bills_created' => $userBillsCreated,
                            'timestamp' => (int)(microtime(true) * 1000)
                        ]),
                        $user['email'] ?? 'system',
                        'System Cron',
                        'bills',
                        $canonicalMonth,
                        'SUCCESS',
                        (int)(microtime(true) * 1000)
                    ]);
                } catch (Throwable $ae) {
                    // Non-blocking audit log record
                }
            }

        } catch (Throwable $ue) {
            $summary['users_failed']++;
            $summary['errors'][] = "User $userId Failed: " . $ue->getMessage();
        }
    }

} catch (Throwable $ge) {
    $summary['errors'][] = "General Scheduler Error: " . $ge->getMessage();
}

// Release lock
flock($lockHandle, LOCK_UN);
fclose($lockHandle);
if (file_exists($lockFile)) {
    @unlink($lockFile);
}

$executionEnd = microtime(true);
$duration = round($executionEnd - $executionStart, 3);

echo "===============================================================\n";
echo " SCHEDULER EXECUTION SUMMARY\n";
echo "===============================================================\n";
echo "Total Users in System   : " . $summary['users_total'] . "\n";
echo "Users Processed         : " . $summary['users_processed'] . "\n";
echo "Users Failed            : " . $summary['users_failed'] . "\n";
echo "Customers Checked       : " . $summary['customers_checked'] . "\n";
echo "Customers Eligible      : " . $summary['customers_eligible'] . "\n";
echo "Skipped (Inactive/Susp) : " . $summary['customers_skipped_inactive_suspended'] . "\n";
echo "Skipped (Free Package)  : " . $summary['customers_skipped_free_package'] . "\n";
echo "New Bills Created       : " . $summary['bills_created'] . "\n";
echo "Existing Bills Skipped  : " . $summary['bills_already_existed'] . "\n";
echo "Duplicate Race Skips    : " . $summary['bills_duplicate_race_skipped'] . "\n";
echo "Errors Encountered      : " . count($summary['errors']) . "\n";
echo "Execution Duration      : {$duration}s\n";
echo "---------------------------------------------------------------\n";

if (!empty($summary['errors'])) {
    echo "ERROR DETAILS:\n";
    foreach ($summary['errors'] as $err) {
        echo " - $err\n";
    }
}
echo "Scheduler run completed.\n";
