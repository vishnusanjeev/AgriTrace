<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$in = json_in();
$batchId = (int)($in['batch_id'] ?? 0);
if ($batchId <= 0) json_out(['ok' => false, 'error' => 'batch_id required'], 422);

try {
    $pdo->beginTransaction();

    $st = $pdo->prepare("SELECT id, status FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1
                         FOR UPDATE");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Batch not assigned to distributor'], 403);
    }

    $status = (string)$transfer['status'];
    if ($status !== 'IN_TRANSIT') {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Transfer not accepted'], 409);
    }

    $st = $pdo->prepare("UPDATE batch_transfers SET status='PICKED_UP', updated_at=NOW() WHERE id=?");
    $st->execute([(int)$transfer['id']]);

    $meta = ['batch_id' => $batchId];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'PICKUP_CONFIRMED',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    $pdo->commit();

    json_out(['ok' => true, 'status' => 'PICKED_UP']);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $resp = ['ok' => false, 'error' => 'Failed to confirm pickup'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
