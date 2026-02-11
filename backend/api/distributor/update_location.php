<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$in = json_in();
$batchId = (int)($in['batch_id'] ?? 0);
$location = trim((string)($in['location_text'] ?? ''));
$temperature = $in['temperature_c'] ?? null;
$remarks = trim((string)($in['remarks'] ?? ''));

if ($batchId <= 0) json_out(['ok' => false, 'error' => 'batch_id required'], 422);
if ($location === '') json_out(['ok' => false, 'error' => 'location_text required'], 422);

try {
    $st = $pdo->prepare("SELECT id, status FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) json_out(['ok' => false, 'error' => 'Batch not assigned to distributor'], 403);

    $status = (string)$transfer['status'];
    if (in_array($status, ['REJECTED', 'CANCELLED'], true)) {
        json_out(['ok' => false, 'error' => 'Transfer not active'], 403);
    }

    $meta = [
        'location_text' => $location,
        'temperature_c' => $temperature,
        'remarks' => $remarks
    ];

    $st = $pdo->prepare("INSERT INTO batch_location_updates (batch_id, actor_user_id, location_text, temperature_c, remarks)
                         VALUES (?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $location,
        is_null($temperature) ? null : (float)$temperature,
        $remarks !== '' ? $remarks : null
    ]);

    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'DISTRIBUTOR_UPDATE_LOCATION',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    json_out(['ok' => true]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to update location'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
