<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER', 'RETAILER']);
$in = json_in();

$batchId = (int)($in['batch_id'] ?? 0);
if ($batchId <= 0) json_out(['ok' => false, 'error' => 'Batch ID required'], 400);

$pdo = pdo();

try {
    $st = $pdo->prepare("SELECT id, batch_code, created_by_user_id FROM batches WHERE id=? LIMIT 1");
    $st->execute([$batchId]);
    $batch = $st->fetch();
    if (!$batch) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    if ($auth['role'] === 'FARMER' && (int)$batch['created_by_user_id'] !== (int)$auth['uid']) {
        json_out(['ok' => false, 'error' => 'Forbidden'], 403);
    }

    $st = $pdo->prepare("SELECT hash_hex FROM batch_hash_snapshots WHERE batch_id=? ORDER BY version DESC, id DESC LIMIT 1");
    $st->execute([$batchId]);
    $snap = $st->fetch();
    if (!$snap) json_out(['ok' => false, 'error' => 'Batch hash not found'], 404);

    $hashHex = $snap['hash_hex'];
    $nonce = bin2hex(random_bytes(6));
    $payload = '';

    for ($i = 0; $i < 3; $i++) {
        $payload = 'AGRITRACE|v1|batch_id=' . $batchId . '|batch_code=' . $batch['batch_code'] . '|hash=' . $hashHex . '|nonce=' . $nonce;
        $st = $pdo->prepare("SELECT id FROM qr_codes WHERE qr_payload=? LIMIT 1");
        $st->execute([$payload]);
        if (!$st->fetch()) break;
        $nonce = bin2hex(random_bytes(6));
    }

    $st = $pdo->prepare("INSERT INTO qr_codes (batch_id, generated_by_user_id, qr_payload) VALUES (?,?,?)");
    $st->execute([$batchId, (int)$auth['uid'], $payload]);
    $id = (int)$pdo->lastInsertId();

    json_out([
        'ok' => true,
        'qr' => [
            'id' => $id,
            'batch_id' => $batchId,
            'qr_payload' => $payload
        ]
    ]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Failed to generate QR'], 500);
}
