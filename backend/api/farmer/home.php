<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER']);
$pdo = pdo();

try {
    // keep existing active count logic
    $st = $pdo->prepare("SELECT COUNT(*) FROM batches WHERE created_by_user_id = ? AND status = 'ACTIVE'");
    $st->execute([(int)$auth['uid']]);
    $activeCount = (int)$st->fetchColumn();

    // keep existing qr count logic (schema-adaptive)
    $qrCount = 0;
    $st = $pdo->prepare("SELECT column_name FROM information_schema.columns
                         WHERE table_schema = ? AND table_name = ? AND column_name IN (?, ?, ?)");
    $st->execute([DB_NAME, 'qr_codes', 'consumer_scan_count', 'distributor_scan_count', 'retailer_scan_count']);
    $cols = $st->fetchAll(PDO::FETCH_COLUMN);
    $hasConsumerCount = in_array('consumer_scan_count', $cols, true);
    $hasDistributorCount = in_array('distributor_scan_count', $cols, true);
    $hasRetailerCount = in_array('retailer_scan_count', $cols, true);

    if ($hasConsumerCount || $hasDistributorCount || $hasRetailerCount) {
        $sumExpr = [];
        if ($hasConsumerCount) $sumExpr[] = "q.consumer_scan_count";
        if ($hasDistributorCount) $sumExpr[] = "q.distributor_scan_count";
        if ($hasRetailerCount) $sumExpr[] = "q.retailer_scan_count";
        $sumSql = implode(' + ', $sumExpr);

        $st = $pdo->prepare("SELECT COALESCE(SUM($sumSql), 0) FROM qr_codes q
                             JOIN batches b ON b.id = q.batch_id
                             WHERE b.created_by_user_id = ?");
        $st->execute([(int)$auth['uid']]);
        $qrCount = (int)$st->fetchColumn();
    } else {
        $st = $pdo->prepare("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?");
        $st->execute([DB_NAME, 'qr_scan_events']);
        $hasQrTable = (int)$st->fetchColumn() > 0;

        if ($hasQrTable) {
            $st = $pdo->prepare("SELECT COUNT(*) FROM qr_scan_events q
                                 JOIN batches b ON b.id = q.batch_id
                                 WHERE b.created_by_user_id = ?");
            $st->execute([(int)$auth['uid']]);
            $qrCount = (int)$st->fetchColumn();
        }
    }

    // ✅ recent batches + latest transfer (ADD-ONLY; does not change existing fields)
    $st = $pdo->prepare("
        SELECT
            b.id, b.batch_code, b.status, b.created_at,
            b.quantity_kg, b.harvest_date,
            c.crop_name,
            t.status AS transfer_status,
            t.updated_at AS transfer_updated_at,
            t.to_user_id AS transfer_to_user_id,
            u.full_name AS transfer_to_user_name
        FROM batches b
        JOIN crops c ON c.id = b.crop_id
        LEFT JOIN batch_transfers t
          ON t.id = (
              SELECT t2.id
              FROM batch_transfers t2
              WHERE t2.batch_id = b.id
              ORDER BY t2.updated_at DESC, t2.id DESC
              LIMIT 1
          )
        LEFT JOIN users u ON u.id = t.to_user_id
        WHERE b.created_by_user_id = ?
        ORDER BY b.created_at DESC
        LIMIT 3
    ");
    $st->execute([(int)$auth['uid']]);
    $rows = $st->fetchAll(PDO::FETCH_ASSOC);

    $recent = [];
    foreach ($rows as $r) {
        // Compute display status: prioritize transfer status over batch status
        // Same logic as distributor - show transfer status when available (unless rejected/cancelled)
        $displayStatus = $r['status']; // default to batch status
        if (!empty($r['transfer_status'])) {
            $transferStatus = $r['transfer_status'];
            // Use transfer status if it's not REJECTED or CANCELLED
            if (!in_array($transferStatus, ['REJECTED', 'CANCELLED'], true)) {
                $displayStatus = $transferStatus; // IN_TRANSIT, PICKED_UP, ASSIGNED
            }
        }
        
        $recent[] = [
            'id' => (int)$r['id'],
            'batch_code' => $r['batch_code'],
            'crop_name' => $r['crop_name'],
            'status' => $displayStatus, // ✅ Now shows transfer status (IN_TRANSIT, PICKED_UP) when applicable
            'quantity_kg' => $r['quantity_kg'],
            'harvest_date' => $r['harvest_date'],
            'created_at' => $r['created_at'],

            // ✅ additive payload (safe for old apps)
            'latest_transfer' => !empty($r['transfer_status']) ? [
                'status' => $r['transfer_status'],
                'updated_at' => $r['transfer_updated_at'] ?? null,
                'to_user_id' => $r['transfer_to_user_id'] !== null ? (int)$r['transfer_to_user_id'] : null,
                'to_user_name' => $r['transfer_to_user_name'] ?? null
            ] : null
        ];
    }

    json_out([
        'ok' => true,
        'stats' => [
            'active_batches' => $activeCount,
            'qr_scans' => $qrCount
        ],
        'recent' => $recent
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load dashboard'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
