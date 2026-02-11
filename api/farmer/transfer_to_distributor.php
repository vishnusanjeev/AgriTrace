<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER']);
$pdo = pdo();

$in = json_in();
$batchId = (int)($in['batch_id'] ?? 0);
$distributorId = (int)($in['distributor_id'] ?? 0);

if ($batchId <= 0) json_out(['ok' => false, 'error' => 'batch_id required'], 422);
if ($distributorId <= 0) json_out(['ok' => false, 'error' => 'distributor_id required'], 422);

try {
    $st = $pdo->prepare("SELECT id, created_by_user_id FROM batches WHERE id=? LIMIT 1");
    $st->execute([$batchId]);
    $batch = $st->fetch();
    if (!$batch) json_out(['ok' => false, 'error' => 'Batch not found'], 404);
    if ((int)$batch['created_by_user_id'] !== (int)$auth['uid']) {
        json_out(['ok' => false, 'error' => 'Forbidden'], 403);
    }

    $st = $pdo->prepare("SELECT id, role FROM users WHERE id=? LIMIT 1");
    $st->execute([$distributorId]);
    $u = $st->fetch();
    if (!$u || strtoupper((string)$u['role']) !== 'DISTRIBUTOR') {
        json_out(['ok' => false, 'error' => 'Distributor not found'], 404);
    }

    $st = $pdo->prepare("INSERT INTO batch_transfers (batch_id, from_user_id, to_user_id, status)
                         VALUES (?,?,?, 'ASSIGNED')");
    $st->execute([$batchId, (int)$auth['uid'], $distributorId]);

    json_out(['ok' => true]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to transfer batch'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
