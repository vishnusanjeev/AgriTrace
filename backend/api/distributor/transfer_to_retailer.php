<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$in = json_in();
$batchId = (int)($in['batch_id'] ?? 0);
$retailerId = (int)($in['retailer_id'] ?? 0);
if ($batchId <= 0 || $retailerId <= 0) {
    json_out(['ok' => false, 'error' => 'batch_id and retailer_id required'], 422);
}

try {
    $pdo->beginTransaction();

    $st = $pdo->prepare("SELECT id, role, is_active FROM users WHERE id = ? LIMIT 1");
    $st->execute([$retailerId]);
    $retailer = $st->fetch();
    if (!$retailer || $retailer['role'] !== 'RETAILER' || (int)$retailer['is_active'] !== 1) {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Retailer not available'], 404);
    }

    $st = $pdo->prepare("SELECT id, status FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1
                         FOR UPDATE");
    $st->execute([$batchId, (int)$auth['uid']]);
    $current = $st->fetch();
    if (!$current || $current['status'] !== 'PICKED_UP') {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Batch not picked up by distributor'], 403);
    }

    $st = $pdo->prepare("SELECT id FROM batch_transfers
                         WHERE batch_id = ? AND from_user_id = ? AND to_user_id = ? AND status = 'ASSIGNED'
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid'], $retailerId]);
    $existing = $st->fetch();
    if ($existing) {
        $pdo->commit();
        json_out(['ok' => true, 'transfer_id' => (int)$existing['id']]);
    }

    $st = $pdo->prepare("INSERT INTO batch_transfers (batch_id, from_user_id, to_user_id, status)
                         VALUES (?,?,?, 'ASSIGNED')");
    $st->execute([$batchId, (int)$auth['uid'], $retailerId]);
    $transferId = (int)$pdo->lastInsertId();

    $meta = ['batch_id' => $batchId, 'retailer_id' => $retailerId];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'RETAILER_ASSIGNED',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    $pdo->commit();
    json_out(['ok' => true, 'transfer_id' => $transferId]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $resp = ['ok' => false, 'error' => 'Failed to transfer to retailer'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
