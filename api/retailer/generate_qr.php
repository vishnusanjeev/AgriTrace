<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$in = json_in();

$batchId = (int)($in['batch_id'] ?? 0);
if ($batchId <= 0) json_out(['ok' => false, 'error' => 'Batch ID required'], 422);

$pdo = pdo();

try {
    $st = $pdo->prepare("SELECT id FROM batches WHERE id=? LIMIT 1");
    $st->execute([$batchId]);
    $batch = $st->fetch();
    if (!$batch) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $payload = json_encode(['batch_id' => $batchId, 'v' => 1], JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

    $st = $pdo->prepare("INSERT INTO qr_codes (batch_id, generated_by_user_id, qr_payload, is_active) VALUES (?,?,?,1)");
    $st->execute([$batchId, (int)$auth['uid'], $payload]);
    $id = (int)$pdo->lastInsertId();

    json_out([
        'ok' => true,
        'qr' => [
            'id' => $id,
            'qr_payload' => $payload
        ]
    ]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Failed to generate QR'], 500);
}
