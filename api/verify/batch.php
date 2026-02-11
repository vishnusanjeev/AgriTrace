<?php
require_once __DIR__ . '/../_bootstrap.php';
require_once __DIR__ . '/../_chain_client.php';

$qrPayload = trim((string)($_GET['qr_payload'] ?? ''));
$batchCode = trim((string)($_GET['batch_code'] ?? ''));

$batchId = 0;
$qrRow = null;

if ($qrPayload !== '') {
    $parts = explode('|', $qrPayload);
    $data = [];
    foreach ($parts as $p) {
        if (strpos($p, '=') !== false) {
            [$k, $v] = explode('=', $p, 2);
            $data[$k] = $v;
        }
    }
    if (!empty($data['batch_id'])) {
        $batchId = (int)$data['batch_id'];
    }
    if (!empty($data['batch_code'])) {
        $batchCode = $data['batch_code'];
    }

    if ($batchId <= 0 && $batchCode === '') {
        $json = json_decode($qrPayload, true);
        if (is_array($json)) {
            if (!empty($json['batch_id'])) $batchId = (int)$json['batch_id'];
            if (!empty($json['batch_code'])) $batchCode = (string)$json['batch_code'];
        }
    }
}

if ($batchId <= 0 && $batchCode === '') {
    json_out(['ok' => false, 'error' => 'qr_payload or batch_code required'], 400);
}

$pdo = pdo();

try {
    if ($qrPayload !== '') {
        $st = $pdo->prepare("SELECT id, batch_id, consumer_scan_count, distributor_scan_count, retailer_scan_count,
                                    last_scanned_at, last_scanned_by_user_id, last_scanned_role
                             FROM qr_codes
                             WHERE qr_payload = ? AND is_active = 1
                             LIMIT 1");
        $st->execute([$qrPayload]);
        $qrRow = $st->fetch();
        if (!$qrRow) {
            json_out(['ok' => false, 'error' => 'QR not found'], 404);
        }
        if ($batchId <= 0) {
            $batchId = (int)$qrRow['batch_id'];
        }
    }

    if ($batchId > 0) {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status, c.crop_name, c.category
                             FROM batches b JOIN crops c ON c.id = b.crop_id WHERE b.id=? LIMIT 1");
        $st->execute([$batchId]);
    } else {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status, c.crop_name, c.category
                             FROM batches b JOIN crops c ON c.id = b.crop_id WHERE b.batch_code=? LIMIT 1");
        $st->execute([$batchCode]);
    }

    $batch = $st->fetch();
    if (!$batch) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $batchId = (int)$batch['id'];
    $batchCode = $batch['batch_code'];

    $st = $pdo->prepare("SELECT hash_hex FROM batch_hash_snapshots WHERE batch_id=? ORDER BY version DESC, id DESC LIMIT 1");
    $st->execute([$batchId]);
    $snap = $st->fetch();
    if (!$snap) json_out(['ok' => false, 'error' => 'Batch hash not found'], 404);

    $dbHash = strtolower($snap['hash_hex']);

    $bridge = chain_get('/chain/batch/' . rawurlencode($batchCode));
    if ($bridge['ok']) {
        $h = strtolower((string)($bridge['data']['hash_hex'] ?? ''));
        $h = ltrim($h, '0x');
        if (($h === '' || $h === str_repeat('0', 64)) && $batchId > 0) {
            $bridge = chain_get('/chain/batch/' . rawurlencode((string)$batchId));
        }
    }
    if (!$bridge['ok']) {
        json_out([
            'ok' => true,
            'status' => 'CHAIN_UNAVAILABLE',
            'batch' => [
                'batch_code' => $batchCode,
                'crop_name' => $batch['crop_name'],
                'category' => $batch['category'],
                'quantity_kg' => $batch['quantity_kg'],
                'harvest_date' => $batch['harvest_date']
            ],
            'chain' => ['error' => $bridge['error'] ?? 'Bridge unavailable']
        ]);
    }

    $data = $bridge['data'];
    $chainHash = strtolower((string)($data['hash_hex'] ?? ''));
    $chainHash = ltrim($chainHash, '0x');

    $status = 'CHAIN_NOT_FOUND';
    if ($chainHash !== '' && $chainHash !== str_repeat('0', 64)) {
        $status = ($chainHash === $dbHash) ? 'BLOCKCHAIN_VERIFIED' : 'DATA_TAMPERED';
    }

    $token = get_bearer_token();
    $payload = $token ? jwt_verify($token) : null;
    $role = strtoupper((string)($payload['role'] ?? ''));
    $uid = (int)($payload['uid'] ?? 0);
    $scanResult = ($status === 'BLOCKCHAIN_VERIFIED') ? 'SUCCESS' : 'FAIL';

    if ($uid > 0) {
        $meta = [
            'qr_payload' => $qrPayload,
            'status' => $status
        ];
        $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                             VALUES (?,?,?,?,?,?)");
        $st->execute([
            $batchId,
            $uid,
            $role,
            'QR_VERIFY',
            $scanResult,
            json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
        ]);
    }

    $scanUpdated = false;
    $counts = null;
    if ($status === 'BLOCKCHAIN_VERIFIED' && $qrPayload !== '') {
        $st = $pdo->prepare("SELECT COUNT(*) FROM information_schema.columns
                             WHERE table_schema = ? AND table_name = ? AND column_name = ?");
        $st->execute([DB_NAME, 'qr_codes', 'consumer_scan_count']);
        $hasScanCols = (int)$st->fetchColumn() > 0;

        $col = null;
        if ($role === 'CONSUMER' || $role === 'CUSTOMER') $col = 'consumer_scan_count';
        if ($role === 'DISTRIBUTOR') $col = 'distributor_scan_count';
        if ($role === 'RETAILER') $col = 'retailer_scan_count';

        if ($col && $uid > 0 && $hasScanCols) {
            $debounce = false;
            if ($qrRow) {
                $lastBy = (int)($qrRow['last_scanned_by_user_id'] ?? 0);
                $lastAt = $qrRow['last_scanned_at'] ?? null;
                if ($lastBy === $uid && $lastAt) {
                    $st = $pdo->prepare("SELECT TIMESTAMPDIFF(SECOND, ?, NOW()) AS diff_sec");
                    $st->execute([$lastAt]);
                    $diff = (int)$st->fetchColumn();
                    if ($diff >= 0 && $diff <= 5) $debounce = true;
                }
            }

            if (!$debounce) {
                $st = $pdo->prepare("UPDATE qr_codes
                                     SET $col = $col + 1,
                                         last_scanned_at = NOW(),
                                         last_scanned_by_user_id = ?,
                                         last_scanned_role = ?
                                     WHERE qr_payload = ?
                                     LIMIT 1");
                $st->execute([$uid, $role, $qrPayload]);
                $scanUpdated = $st->rowCount() > 0;
            }

            $st = $pdo->prepare("SELECT consumer_scan_count, distributor_scan_count, retailer_scan_count
                                 FROM qr_codes WHERE qr_payload = ? LIMIT 1");
            $st->execute([$qrPayload]);
            $counts = $st->fetch();
        }
    }

    json_out([
        'ok' => true,
        'status' => $status,
        'batch' => [
            'id' => $batchId,
            'batch_code' => $batchCode,
            'crop_name' => $batch['crop_name'],
            'category' => $batch['category'],
            'quantity_kg' => $batch['quantity_kg'],
            'harvest_date' => $batch['harvest_date']
        ],
        'chain' => [
            'chain_id' => $data['chain_id'] ?? 'local',
            'hash_hex' => $data['hash_hex'] ?? null
        ],
        'scan' => [
            'updated' => $scanUpdated,
            'counts' => $counts ? [
                'consumer' => (int)$counts['consumer_scan_count'],
                'distributor' => (int)$counts['distributor_scan_count'],
                'retailer' => (int)$counts['retailer_scan_count']
            ] : null
        ]
    ]);
} catch (Throwable $e) {
    json_out(['ok' => false, 'error' => 'Verification failed'], 500);
}
