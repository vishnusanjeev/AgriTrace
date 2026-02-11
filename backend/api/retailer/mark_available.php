<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();
$in = json_in();

$batchId = (int)($in['batch_id'] ?? 0);
if ($batchId <= 0) json_out(['ok' => false, 'error' => 'batch_id required'], 422);

try {
    $st = $pdo->prepare("SELECT id, status, to_user_id FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) json_out(['ok' => false, 'error' => 'Transfer not found'], 404);

    if ((string)$transfer['status'] !== 'PICKED_UP') {
        json_out(['ok' => false, 'error' => 'Batch not received'], 403);
    }

    $pdo->beginTransaction();

    $st = $pdo->prepare("UPDATE batches SET status='ACTIVE', updated_at=NOW() WHERE id=?");
    $st->execute([$batchId]);

    $meta = ['batch_id' => $batchId];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'RETAILER_MARKED_AVAILABLE',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    $pdo->commit();
    json_out(['ok' => true]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $resp = ['ok' => false, 'error' => 'Failed to mark available'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
