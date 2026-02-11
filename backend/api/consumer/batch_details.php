<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['CONSUMER']);
$pdo = pdo();

$batchId = (int)($_GET['batch_id'] ?? 0);
$batchCode = trim((string)($_GET['batch_code'] ?? ''));

if ($batchId <= 0 && $batchCode === '') {
    json_out(['ok' => false, 'error' => 'batch_id or batch_code required'], 422);
}

try {
    if ($batchId <= 0 && $batchCode !== '') {
        $st = $pdo->prepare("SELECT id FROM batches WHERE batch_code = ? LIMIT 1");
        $st->execute([$batchCode]);
        $row = $st->fetch();
        $batchId = (int)($row['id'] ?? 0);
    }

    if ($batchId <= 0) {
        json_out(['ok' => false, 'error' => 'Batch not found'], 404);
    }

    $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.status, b.is_organic,
                                c.crop_name, c.category,
                                u.full_name AS farmer_name, u.location AS farmer_location
                         FROM batches b
                         JOIN crops c ON c.id = b.crop_id
                         JOIN users u ON u.id = b.created_by_user_id
                         WHERE b.id = ?
                         LIMIT 1");
    $st->execute([$batchId]);
    $batch = $st->fetch();
    if (!$batch) {
        json_out(['ok' => false, 'error' => 'Batch not found'], 404);
    }

    $st = $pdo->prepare("SELECT hash_hex FROM batch_hash_snapshots
                         WHERE batch_id = ?
                         ORDER BY version DESC, id DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $snap = $st->fetch();
    if (!$snap) {
        json_out(['ok' => false, 'error' => 'Batch hash not found'], 404);
    }

    $dbHash = strtolower($snap['hash_hex']);

    $st = $pdo->prepare("SELECT tx_hash, status, confirmed_at, payload_hash_hex
                         FROM blockchain_events
                         WHERE batch_id = ?
                         ORDER BY COALESCE(confirmed_at, created_at) DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $chain = $st->fetch();

    $chainHash = strtolower((string)($chain['payload_hash_hex'] ?? ''));
    $verified = false;
    $verificationReason = 'Chain record missing';
    if ($chainHash !== '') {
        $verified = ($chainHash === $dbHash);
        $verificationReason = $verified ? 'Verified against blockchain' : 'Hash mismatch with blockchain';
    }

    $journey = [];
    $st = $pdo->prepare("SELECT event_name, confirmed_at, created_at
                         FROM blockchain_events
                         WHERE batch_id = ? AND event_name = 'BatchCreated'
                         ORDER BY COALESCE(confirmed_at, created_at) DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $harvest = $st->fetch();
    $journey[] = [
        'title' => 'Harvested',
        'subtitle' => $batch['farmer_name'] ?: 'Farmer',
        'time' => $harvest['confirmed_at'] ?? $harvest['created_at'] ?? $batch['harvest_date']
    ];

    $st = $pdo->prepare("SELECT COUNT(*) FROM batch_scan_events
                         WHERE batch_id = ? AND event_type = 'TRANSPORT_UPDATE'");
    $st->execute([$batchId]);
    $hasTransit = (int)$st->fetchColumn() > 0;
    $journey[] = [
        'title' => 'In Transit',
        'subtitle' => $hasTransit ? 'Transport updates available' : 'Not yet started',
        'time' => null
    ];

    $st = $pdo->prepare("SELECT u.full_name AS retailer_name, t.updated_at
                         FROM batch_transfers t
                         JOIN users u ON u.id = t.to_user_id
                         WHERE t.batch_id = ? AND u.role = 'RETAILER'
                         ORDER BY t.id DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $ret = $st->fetch();
    $journey[] = [
        'title' => 'Retail Store',
        'subtitle' => $ret['retailer_name'] ?? 'Retailer',
        'time' => $ret['updated_at'] ?? null
    ];

    json_out([
        'ok' => true,
        'data' => [
            'batch_id' => $batchId,
            'batch_code' => $batch['batch_code'],
            'crop_name' => $batch['crop_name'],
            'category' => $batch['category'],
            'quantity_kg' => $batch['quantity_kg'],
            'harvest_date' => $batch['harvest_date'],
            'is_organic' => (int)$batch['is_organic'],
            'farmer_name' => $batch['farmer_name'],
            'farmer_location' => $batch['farmer_location'],
            'verified' => $verified,
            'verification_reason' => $verificationReason,
            'chain' => [
                'tx_hash' => $chain['tx_hash'] ?? null,
                'status' => $chain['status'] ?? null,
                'confirmed_at' => $chain['confirmed_at'] ?? null
            ],
            'journey_summary' => $journey
        ]
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load batch'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
