<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();

$in = json_in();
$batchId = (int)($in['batch_id'] ?? 0);
$storeLocation = trim((string)($in['store_location'] ?? ''));
$dateTime = trim((string)($in['date_time'] ?? ''));
if ($batchId <= 0) json_out(['ok' => false, 'error' => 'batch_id required'], 422);
if ($storeLocation === '' || $dateTime === '') {
    json_out(['ok' => false, 'error' => 'store_location and date_time required'], 422);
}

try {
    $st = $pdo->prepare("SELECT id, status, to_user_id FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) json_out(['ok' => false, 'error' => 'Transfer not found'], 404);

    $status = (string)$transfer['status'];
    if ($status !== 'ASSIGNED' && $status !== 'PICKED_UP') {
        json_out(['ok' => false, 'error' => 'Batch not assigned to retailer'], 403);
    }

    $pdo->beginTransaction();

    if ($status === 'ASSIGNED') {
        $st = $pdo->prepare("UPDATE batch_transfers SET status='PICKED_UP', updated_at=NOW() WHERE id=?");
        $st->execute([(int)$transfer['id']]);
    }

    $meta = [
        'batch_id' => $batchId,
        'store_location' => $storeLocation,
        'date_time' => $dateTime
    ];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'RETAILER_RECEIPT_CONFIRMED',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    $st = $pdo->prepare("SELECT id, qr_payload FROM qr_codes
                         WHERE batch_id = ? AND generated_by_user_id = ? AND is_active = 1
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $qrRow = $st->fetch();

    $qrPayload = $qrRow ? (string)$qrRow['qr_payload'] : '';
    if ($qrPayload === '') {
        $st = $pdo->prepare("SELECT batch_code FROM batches WHERE id=? LIMIT 1");
        $st->execute([$batchId]);
        $batch = $st->fetch();
        if (!$batch) {
            $pdo->rollBack();
            json_out(['ok' => false, 'error' => 'Batch not found'], 404);
        }

        $st = $pdo->prepare("SELECT hash_hex FROM batch_hash_snapshots WHERE batch_id=? ORDER BY version DESC, id DESC LIMIT 1");
        $st->execute([$batchId]);
        $snap = $st->fetch();
        if (!$snap) {
            $pdo->rollBack();
            json_out(['ok' => false, 'error' => 'Batch hash not found'], 404);
        }

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

        $st = $pdo->prepare("INSERT INTO qr_codes (batch_id, generated_by_user_id, qr_payload, is_active) VALUES (?,?,?,1)");
        $st->execute([$batchId, (int)$auth['uid'], $payload]);
        $qrPayload = $payload;
    }

    $pdo->commit();
    json_out(['ok' => true, 'status' => 'PICKED_UP', 'qr_payload' => $qrPayload]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $resp = ['ok' => false, 'error' => 'Failed to confirm receipt'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
