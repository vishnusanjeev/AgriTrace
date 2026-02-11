<?php
require_once __DIR__ . '/../_bootstrap.php';
require_once __DIR__ . '/../_chain_client.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$in = json_in();
$qrPayload = trim((string)($in['qr_payload'] ?? ''));
if ($qrPayload === '') {
    json_out(['ok' => false, 'error' => 'qr_payload required'], 422);
}

$batchId = 0;
$batchCode = '';
$qrRow = null;

$parts = explode('|', $qrPayload);
$data = [];
foreach ($parts as $p) {
    if (strpos($p, '=') !== false) {
        [$k, $v] = explode('=', $p, 2);
        $data[$k] = $v;
    }
}
if (!empty($data['batch_id'])) $batchId = (int)$data['batch_id'];
if (!empty($data['batch_code'])) $batchCode = (string)$data['batch_code'];

if ($batchId <= 0 && $batchCode === '') {
    $json = json_decode($qrPayload, true);
    if (is_array($json)) {
        if (!empty($json['batch_id'])) $batchId = (int)$json['batch_id'];
        if (!empty($json['batch_code'])) $batchCode = (string)$json['batch_code'];
    }
}

if ($batchId <= 0 && $batchCode === '') {
    json_out(['ok' => false, 'error' => 'Invalid qr_payload'], 400);
}

try {
    $st = $pdo->prepare("SELECT id, batch_id, consumer_scan_count, distributor_scan_count, retailer_scan_count,
                                last_scanned_at, last_scanned_by_user_id, last_scanned_role
                         FROM qr_codes
                         WHERE qr_payload = ? AND is_active = 1
                         LIMIT 1");
    $st->execute([$qrPayload]);
    $qrRow = $st->fetch();

    if ($batchId > 0) {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status,
                                    c.crop_name, c.category,
                                    u.full_name AS farmer_name, u.location AS farmer_location
                             FROM batches b
                             JOIN crops c ON c.id = b.crop_id
                             JOIN users u ON u.id = b.created_by_user_id
                             WHERE b.id = ? LIMIT 1");
        $st->execute([$batchId]);
    } else {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status,
                                    c.crop_name, c.category,
                                    u.full_name AS farmer_name, u.location AS farmer_location
                             FROM batches b
                             JOIN crops c ON c.id = b.crop_id
                             JOIN users u ON u.id = b.created_by_user_id
                             WHERE b.batch_code = ? LIMIT 1");
        $st->execute([$batchCode]);
    }
    $batch = $st->fetch();
    if (!$batch) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $batchId = (int)$batch['id'];
    $batchCode = (string)$batch['batch_code'];

    $st = $pdo->prepare("SELECT id, status FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) {
        json_out(['ok' => false, 'error' => 'Batch not assigned to distributor'], 403);
    }
    $transferStatus = (string)$transfer['status'];
    if (in_array($transferStatus, ['REJECTED', 'CANCELLED'], true)) {
        json_out(['ok' => false, 'error' => 'Transfer not active'], 403);
    }

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

    $verifiedStatus = 'CHAIN_UNAVAILABLE';
    $chainHash = '';
    if ($bridge['ok']) {
        $chainHash = strtolower((string)($bridge['data']['hash_hex'] ?? ''));
        $chainHash = ltrim($chainHash, '0x');
        if ($chainHash === '' || $chainHash === str_repeat('0', 64)) {
            $verifiedStatus = 'CHAIN_NOT_FOUND';
        } else {
            $verifiedStatus = ($chainHash === $dbHash) ? 'BLOCKCHAIN_VERIFIED' : 'DATA_TAMPERED';
        }
    }

    $result = ($verifiedStatus === 'BLOCKCHAIN_VERIFIED') ? 'OK' : 'FAIL';
    $scanResult = ($verifiedStatus === 'BLOCKCHAIN_VERIFIED') ? 'SUCCESS' : 'FAIL';

    $meta = [
        'qr_payload' => $qrPayload,
        'status' => $verifiedStatus,
        'batch_code' => $batchCode
    ];
    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'QR_VERIFY',
        $scanResult,
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);
    $st = $pdo->prepare("SELECT id FROM batch_scan_events
                         WHERE batch_id = ? AND actor_user_id = ? AND event_type = 'DISTRIBUTOR_SCAN_VERIFY'
                           AND result = 'OK' AND created_at >= DATE_SUB(NOW(), INTERVAL 30 SECOND)
                         LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $recent = $st->fetch();

    if (!$recent) {
        $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                             VALUES (?,?,?,?,?,?)");
        $st->execute([
            $batchId,
            (int)$auth['uid'],
            $auth['role'],
            'DISTRIBUTOR_SCAN_VERIFY',
            $result,
            json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
        ]);
    }

    $scanUpdated = false;
    $counts = null;
    if ($verifiedStatus === 'BLOCKCHAIN_VERIFIED' && $qrRow) {
        $debounce = false;
        $lastBy = (int)($qrRow['last_scanned_by_user_id'] ?? 0);
        $lastAt = $qrRow['last_scanned_at'] ?? null;
        if ($lastBy === (int)$auth['uid'] && $lastAt) {
            $st = $pdo->prepare("SELECT TIMESTAMPDIFF(SECOND, ?, NOW()) AS diff_sec");
            $st->execute([$lastAt]);
            $diff = (int)$st->fetchColumn();
            if ($diff >= 0 && $diff <= 5) $debounce = true;
        }

        if (!$debounce) {
            $st = $pdo->prepare("UPDATE qr_codes
                                 SET distributor_scan_count = distributor_scan_count + 1,
                                     last_scanned_at = NOW(),
                                     last_scanned_by_user_id = ?,
                                     last_scanned_role = ?
                                 WHERE id = ?
                                 LIMIT 1");
            $st->execute([(int)$auth['uid'], $auth['role'], (int)$qrRow['id']]);
            $scanUpdated = $st->rowCount() > 0;
        }

        $st = $pdo->prepare("SELECT consumer_scan_count, distributor_scan_count, retailer_scan_count
                             FROM qr_codes WHERE id = ? LIMIT 1");
        $st->execute([(int)$qrRow['id']]);
        $counts = $st->fetch();
    }

    json_out([
        'ok' => true,
        'batch' => [
            'id' => (int)$batch['id'],
            'batch_code' => $batchCode,
            'crop_name' => $batch['crop_name'],
            'category' => $batch['category'],
            'quantity_kg' => $batch['quantity_kg'],
            'harvest_date' => $batch['harvest_date'],
            'status' => $batch['status'],
            'farmer_name' => $batch['farmer_name'],
            'farmer_location' => $batch['farmer_location']
        ],
        'verified' => [
            'status' => $verifiedStatus,
            'db_hash' => $dbHash,
            'chain_hash' => $chainHash !== '' ? ('0x' . $chainHash) : ''
        ],
        'transfer' => [
            'status' => $transferStatus
        ],
        'actions' => [
            'canConfirmPickup' => ($verifiedStatus === 'BLOCKCHAIN_VERIFIED' && $transferStatus === 'ASSIGNED')
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
    $resp = ['ok' => false, 'error' => 'Verification failed'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
