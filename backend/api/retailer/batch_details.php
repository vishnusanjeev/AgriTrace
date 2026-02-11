<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['RETAILER']);
$pdo = pdo();

$batchId = isset($_GET['batch_id']) ? (int)$_GET['batch_id'] : 0;
$batchCode = trim((string)($_GET['batch_code'] ?? ''));

if ($batchId <= 0 && $batchCode === '') {
    json_out(['ok' => false, 'error' => 'batch_id or batch_code required'], 422);
}

try {
    if ($batchId > 0) {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date,
                                    c.crop_name, c.category,
                                    u.full_name AS farmer_name, u.location AS farmer_location
                             FROM batches b
                             JOIN crops c ON c.id = b.crop_id
                             JOIN users u ON u.id = b.created_by_user_id
                             WHERE b.id = ?
                             LIMIT 1");
        $st->execute([$batchId]);
    } else {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date,
                                    c.crop_name, c.category,
                                    u.full_name AS farmer_name, u.location AS farmer_location
                             FROM batches b
                             JOIN crops c ON c.id = b.crop_id
                             JOIN users u ON u.id = b.created_by_user_id
                             WHERE b.batch_code = ?
                             LIMIT 1");
        $st->execute([$batchCode]);
    }

    $row = $st->fetch();
    if (!$row) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $batchId = (int)$row['id'];
    $batchCode = $row['batch_code'];

    $st = $pdo->prepare("SELECT id, status, from_user_id
                         FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                           AND status NOT IN ('REJECTED','CANCELLED')
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) json_out(['ok' => false, 'error' => 'Batch not assigned to retailer'], 403);

    $st = $pdo->prepare("SELECT full_name, location FROM users WHERE id = ? LIMIT 1");
    $st->execute([(int)$transfer['from_user_id']]);
    $dist = $st->fetch() ?: [];

    $st = $pdo->prepare("SELECT chain_id, tx_hash, block_number, confirmed_at, created_at
                         FROM blockchain_events
                         WHERE batch_id = ? AND event_name = 'BatchCreated'
                         ORDER BY COALESCE(confirmed_at, created_at) DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $bc = $st->fetch() ?: [];

    $st = $pdo->prepare("SELECT COUNT(*) AS cnt
                         FROM batch_scan_events
                         WHERE batch_id = ? AND actor_user_id = ?
                           AND event_type = 'RETAILER_RECEIPT_CONFIRMED'");
    $st->execute([$batchId, (int)$auth['uid']]);
    $receiptConfirmed = ((int)($st->fetch()['cnt'] ?? 0)) > 0;

    json_out([
        'ok' => true,
        'batch' => [
            'id' => $batchId,
            'batch_code' => $batchCode,
            'crop_name' => $row['crop_name'],
            'category' => $row['category'],
            'quantity_kg' => $row['quantity_kg'],
            'harvest_date' => $row['harvest_date']
        ],
        'farmer' => [
            'name' => $row['farmer_name'],
            'location' => $row['farmer_location']
        ],
        'distributor' => [
            'name' => $dist['full_name'] ?? null,
            'location' => $dist['location'] ?? null
        ],
        'transfer' => [
            'status' => $transfer['status'],
            'receipt_confirmed' => $receiptConfirmed
        ],
        'blockchain' => [
            'chain_id' => $bc['chain_id'] ?? null,
            'tx_hash' => $bc['tx_hash'] ?? null,
            'block_number' => $bc['block_number'] ?? null,
            'block_hash' => null,
            'confirmed_at' => $bc['confirmed_at'] ?? null
        ]
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load batch details'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
