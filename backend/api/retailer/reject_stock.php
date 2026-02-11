<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();

$in = json_in();
$transferId = (int)($in['transfer_id'] ?? 0);
if ($transferId <= 0) json_out(['ok' => false, 'error' => 'transfer_id required'], 422);

try {
    $pdo->beginTransaction();

    $st = $pdo->prepare("SELECT id, batch_id, status FROM batch_transfers
                         WHERE id = ? AND to_user_id = ?
                         FOR UPDATE");
    $st->execute([$transferId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Transfer not found'], 404);
    }

    if ($transfer['status'] !== 'ASSIGNED') {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Already processed'], 409);
    }

    $st = $pdo->prepare("UPDATE batch_transfers SET status='REJECTED', updated_at=NOW() WHERE id=?");
    $st->execute([(int)$transfer['id']]);

    $meta = ['transfer_id' => $transferId];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        (int)$transfer['batch_id'],
        (int)$auth['uid'],
        $auth['role'],
        'RETAILER_REJECTED_STOCK',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    $pdo->commit();
    json_out(['ok' => true]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    $resp = ['ok' => false, 'error' => 'Failed to reject stock'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
