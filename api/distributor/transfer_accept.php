<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$in = json_in();
$transferId = (int)($in['transfer_id'] ?? 0);
if ($transferId <= 0) json_out(['ok' => false, 'error' => 'transfer_id required'], 422);

try {
    $st = $pdo->prepare("SELECT id, batch_id, status FROM batch_transfers WHERE id = ? AND to_user_id = ? LIMIT 1");
    $st->execute([$transferId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) json_out(['ok' => false, 'error' => 'Transfer not found'], 404);

    if ($transfer['status'] !== 'ASSIGNED') {
        json_out(['ok' => false, 'error' => 'Already processed'], 409);
    }

    $st = $pdo->prepare("UPDATE batch_transfers SET status='IN_TRANSIT', updated_at=NOW() WHERE id = ?");
    $st->execute([$transferId]);

    $meta = ['transfer_id' => $transferId, 'batch_id' => (int)$transfer['batch_id']];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        (int)$transfer['batch_id'],
        (int)$auth['uid'],
        $auth['role'],
        'TRANSFER_ACCEPTED',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    json_out(['ok' => true]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to accept transfer'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
