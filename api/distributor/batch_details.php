<?php
require_once __DIR__ . '/../_bootstrap.php';
require_once __DIR__ . '/../_chain_client.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$batchId = isset($_GET['batch_id']) ? (int)$_GET['batch_id'] : 0;
$batchCode = trim((string)($_GET['batch_code'] ?? ''));

if ($batchId <= 0 && $batchCode === '') {
    json_out(['ok' => false, 'error' => 'batch_id or batch_code required'], 422);
}

try {
    if ($batchId > 0) {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.seed_variety,
                                    b.fertilizers_used, b.irrigation_method, b.is_organic, b.status, b.created_at,
                                    c.crop_name, c.category,
                                    u.full_name AS farmer_name, u.location AS farmer_location
                             FROM batches b
                             JOIN crops c ON c.id = b.crop_id
                             JOIN users u ON u.id = b.created_by_user_id
                             WHERE b.id = ?
                             LIMIT 1");
        $st->execute([$batchId]);
    } else {
        $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.seed_variety,
                                    b.fertilizers_used, b.irrigation_method, b.is_organic, b.status, b.created_at,
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

    $st = $pdo->prepare("SELECT id, status FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) json_out(['ok' => false, 'error' => 'Batch not assigned to distributor'], 403);

    $st = $pdo->prepare("SELECT status, created_at, updated_at, to_user_id
                         FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transferInfo = $st->fetch() ?: null;

    $st = $pdo->prepare("SELECT id FROM batch_scan_events
                         WHERE batch_id = ? AND actor_user_id = ? AND event_type = 'TRANSPORT_UPDATE' AND result = 'OK'
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $hasTransport = $st->fetch() ? true : false;

    $st = $pdo->prepare("SELECT t.id, t.to_user_id
                         FROM batch_transfers t
                         JOIN users u ON u.id = t.to_user_id AND u.role = 'RETAILER'
                         WHERE t.batch_id = ? AND t.from_user_id = ?
                           AND t.status NOT IN ('REJECTED','CANCELLED')
                         ORDER BY t.id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $retailerTransfer = $st->fetch();
    $retailerAssigned = $retailerTransfer ? true : false;

    $st = $pdo->prepare("SELECT chain_id, tx_hash, block_number, confirmed_at
                         FROM blockchain_events
                         WHERE batch_id = ? AND event_name = 'BatchCreated'
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId]);
    $bc = $st->fetch() ?: [];

    $blockHash = null;
    $blockNumber = $bc['block_number'] ?? null;
    $txHash = $bc['tx_hash'] ?? null;
    if (!empty($txHash)) {
        $tx = chain_get('/bridge/tx/' . urlencode($txHash));
        if ($tx['ok']) {
            $blockHash = $tx['data']['block_hash'] ?? null;
            if ($blockNumber === null && isset($tx['data']['block_number'])) {
                $blockNumber = $tx['data']['block_number'];
            }
        } else {
            $txHash = null;
        }
    }

    if (empty($txHash)) {
        $st = $pdo->prepare("SELECT hash_hex FROM batch_hash_snapshots
                             WHERE batch_id = ?
                             ORDER BY version DESC, id DESC LIMIT 1");
        $st->execute([$batchId]);
        $snap = $st->fetch();
        if ($snap) {
            $bridge = chain_post('/chain/batchCreated', [
                'batch_code' => $row['batch_code'],
                'hash_hex' => $snap['hash_hex']
            ]);
            if ($bridge['ok']) {
                $data = $bridge['data'];
                $txHash = $data['tx_hash'] ?? null;
                $blockNumber = $data['block_number'] ?? $blockNumber;
                $bc = [
                    'chain_id' => $data['chain_id'] ?? null,
                    'tx_hash' => $txHash,
                    'block_number' => $blockNumber,
                    'confirmed_at' => ($data['status'] ?? '') === 'CONFIRMED' ? now() : null
                ];

                $pdo->prepare("INSERT INTO blockchain_events (event_name, batch_id, chain_id, tx_hash, block_number, payload_hash_hex, status, confirmed_at)
                               VALUES (?,?,?,?,?,?,?,?)")
                    ->execute([
                        'BatchCreated',
                        $batchId,
                        $data['chain_id'] ?? 'local',
                        $txHash ?? '',
                        $blockNumber,
                        $snap['hash_hex'],
                        $data['status'] ?? 'CONFIRMED',
                        ($data['status'] ?? '') === 'CONFIRMED' ? now() : null
                    ]);

                if (($data['status'] ?? '') === 'CONFIRMED') {
                    $pdo->prepare("UPDATE batches SET status='ACTIVE' WHERE id=?")->execute([$batchId]);
                }

                if (!empty($txHash)) {
                    $tx = chain_get('/bridge/tx/' . urlencode($txHash));
                    if ($tx['ok']) {
                        $blockHash = $tx['data']['block_hash'] ?? null;
                        if ($blockNumber === null && isset($tx['data']['block_number'])) {
                            $blockNumber = $tx['data']['block_number'];
                        }
                    }
                }
            }
        }
    }

    $isOrganic = (int)($row['is_organic'] ?? 0) === 1;

    $st = $pdo->prepare("SELECT location_text, temperature_c, remarks, recorded_at
                         FROM batch_location_updates
                         WHERE batch_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId]);
    $loc = $st->fetch() ?: null;

    json_out([
        'ok' => true,
        'batch' => [
            'id' => (int)$row['id'],
            'batch_code' => $row['batch_code'],
            'crop_name' => $row['crop_name'],
            'category' => $row['category'],
            'quantity_kg' => $row['quantity_kg'],
            'harvest_date' => $row['harvest_date'],
            'seed_variety' => $row['seed_variety'],
            'fertilizers_used' => $row['fertilizers_used'],
            'irrigation_method' => $row['irrigation_method'],
            'status' => $row['status'],
            'created_at' => $row['created_at'],
            'product_type' => $isOrganic ? 'Organic' : 'Conventional'
        ],
        'farmer' => [
            'name' => $row['farmer_name'],
            'location' => $row['farmer_location']
        ],
        'transfer' => $transferInfo ? [
            'status' => $transferInfo['status'],
            'created_at' => $transferInfo['created_at'],
            'updated_at' => $transferInfo['updated_at'],
            'to_user_id' => (int)$transferInfo['to_user_id'],
            'transport_updated' => $hasTransport,
            'retailer_assigned' => $retailerAssigned
        ] : null,
        'location_update' => $loc ? [
            'location_text' => $loc['location_text'],
            'temperature_c' => $loc['temperature_c'] !== null ? (float)$loc['temperature_c'] : null,
            'remarks' => $loc['remarks'],
            'recorded_at' => $loc['recorded_at']
        ] : null,
        'blockchain' => [
            'chain_id' => $bc['chain_id'] ?? null,
            'tx_hash' => $bc['tx_hash'] ?? null,
            'block_number' => $blockNumber,
            'block_hash' => $blockHash,
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
