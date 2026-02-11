<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER', 'DISTRIBUTOR', 'RETAILER']);
$in = json_in();

$batchId = (int)($_GET['batch_id'] ?? ($in['batch_id'] ?? 0));
$batchCode = trim((string)($_GET['batch_code'] ?? ($in['batch_code'] ?? '')));

if ($batchId <= 0 && $batchCode === '') {
    json_out(['ok' => false, 'error' => 'Batch ID required'], 400);
}

$pdo = pdo();

try {
    // resolve by batch_code if needed
    if ($batchId <= 0 && $batchCode !== '') {
        $st = $pdo->prepare("SELECT id FROM batches WHERE batch_code = ? LIMIT 1");
        $st->execute([$batchCode]);
        $r = $st->fetch();
        $batchId = (int)($r['id'] ?? 0);
    }

    if ($batchId <= 0) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $st = $pdo->prepare("SELECT b.id, b.batch_code, b.created_by_user_id FROM batches b WHERE b.id=? LIMIT 1");
    $st->execute([$batchId]);
    $batch = $st->fetch();
    if (!$batch) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $role = strtoupper((string)$auth['role']);
    $uid = (int)$auth['uid'];

    // --- AUTHZ (keep existing farmer rule; add safe distributor + retailer rules) ---
    if ($role === 'FARMER') {
        if ((int)$batch['created_by_user_id'] !== $uid) {
            json_out(['ok' => false, 'error' => 'Forbidden'], 403);
        }
    } elseif ($role === 'RETAILER') {
        // Retailer must have an active (non rejected/cancelled) transfer to them
        $st = $pdo->prepare("SELECT id FROM batch_transfers
                             WHERE batch_id = ? AND to_user_id = ?
                               AND status NOT IN ('REJECTED','CANCELLED')
                             ORDER BY id DESC LIMIT 1");
        $st->execute([$batchId, $uid]);
        $t = $st->fetch();
        if (!$t) json_out(['ok' => false, 'error' => 'Forbidden'], 403);
    } elseif ($role === 'DISTRIBUTOR') {
        // Distributor must be legitimately involved:
        // 1) current transfer assigned to them (to_user_id), OR
        // 2) they are the sender in the latest active transfer (holder handing over to retailer)
        $st = $pdo->prepare("SELECT id, from_user_id, to_user_id, status
                             FROM batch_transfers
                             WHERE batch_id = ?
                               AND status NOT IN ('REJECTED','CANCELLED')
                             ORDER BY id DESC LIMIT 1");
        $st->execute([$batchId]);
        $last = $st->fetch();

        if (!$last) {
            // no transfers exist yet => not a distributor-owned batch
            json_out(['ok' => false, 'error' => 'Forbidden'], 403);
        }

        $fromId = (int)($last['from_user_id'] ?? 0);
        $toId   = (int)($last['to_user_id'] ?? 0);

        // if batch is currently assigned to distributor OR distributor is current holder sending it onward
        if ($toId !== $uid && $fromId !== $uid) {
            json_out(['ok' => false, 'error' => 'Forbidden'], 403);
        }
    }

    // --- Fetch QR ---
    $st = $pdo->prepare("SELECT id, qr_payload, generated_by_user_id, created_at
                         FROM qr_codes
                         WHERE batch_id = ?
                         ORDER BY id DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $row = $st->fetch();
    if (!$row) json_out(['ok' => false, 'error' => 'QR not generated yet'], 404);

    json_out([
        'ok' => true,
        'qr' => [
            'id' => (int)$row['id'],
            'batch_id' => $batchId,
            'batch_code' => $batch['batch_code'],
            'qr_payload' => $row['qr_payload'],
            'generated_by_user_id' => (int)$row['generated_by_user_id'],
            'created_at' => $row['created_at']
        ]
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load QR'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
