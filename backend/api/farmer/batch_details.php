<?php
require_once __DIR__ . '/../_bootstrap.php';
require_once __DIR__ . '/../_chain_client.php';

$auth = require_auth(['FARMER']);
$pdo = pdo();

$batchId = isset($_GET['batch_id']) ? (int)$_GET['batch_id'] : 0;
if ($batchId <= 0) {
    json_out(['ok' => false, 'error' => 'batch_id required'], 422);
}

try {
    $st = $pdo->prepare("SELECT b.id, b.batch_code, b.quantity_kg, b.harvest_date, b.seed_variety,
                                b.fertilizers_used, b.irrigation_method, b.is_organic, b.status, b.created_at,
                                c.crop_name, c.category,
                                u.full_name AS farmer_name
                         FROM batches b
                         JOIN crops c ON c.id = b.crop_id
                         JOIN users u ON u.id = b.created_by_user_id
                         WHERE b.id = ? AND b.created_by_user_id = ?
                         LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $row = $st->fetch();
    if (!$row) json_out(['ok' => false, 'error' => 'Batch not found'], 404);

    $st = $pdo->prepare("SELECT chain_id, tx_hash, block_number, confirmed_at
                         FROM blockchain_events
                         WHERE batch_id = ? AND event_name = 'BatchCreated'
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId]);
    $bc = $st->fetch() ?: [];

    // ✅ Fix: latest transfer should be latest of ALL statuses (including REJECTED)
    // Keep output keys same, just correct the source
    $st = $pdo->prepare("SELECT t.id, t.status, t.created_at, t.updated_at,
                                t.to_user_id, u.full_name AS to_user_name, u.phone_e164 AS to_user_phone
                         FROM batch_transfers t
                         JOIN users u ON u.id = t.to_user_id
                         WHERE t.batch_id = ?
                         ORDER BY t.updated_at DESC, t.id DESC
                         LIMIT 1");
    $st->execute([$batchId]);
    $tr = $st->fetch() ?: null;

    // ✅ ADDITIVE: full transfer history for timeline (does not break existing)
    $st = $pdo->prepare("SELECT t.id, t.status, t.created_at, t.updated_at,
                                t.from_user_id, fu.full_name AS from_user_name,
                                t.to_user_id, tu.full_name AS to_user_name
                         FROM batch_transfers t
                         LEFT JOIN users fu ON fu.id = t.from_user_id
                         LEFT JOIN users tu ON tu.id = t.to_user_id
                         WHERE t.batch_id = ?
                         ORDER BY t.created_at ASC, t.id ASC");
    $st->execute([$batchId]);
    $historyRows = $st->fetchAll();

    $transferHistory = [];
    foreach ($historyRows as $h) {
        $transferHistory[] = [
            'id' => (int)$h['id'],
            'status' => $h['status'],
            'created_at' => $h['created_at'],
            'updated_at' => $h['updated_at'],
            'from_user_id' => (int)$h['from_user_id'],
            'from_user_name' => $h['from_user_name'] ?? null,
            'to_user_id' => (int)$h['to_user_id'],
            'to_user_name' => $h['to_user_name'] ?? null
        ];
    }

    $st = $pdo->prepare("SELECT location_text, temperature_c, remarks, recorded_at
                         FROM batch_location_updates
                         WHERE batch_id = ?
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId]);
    $loc = $st->fetch() ?: null;

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
            'name' => $row['farmer_name']
        ],
        'blockchain' => [
            'chain_id' => $bc['chain_id'] ?? null,
            'tx_hash' => $bc['tx_hash'] ?? null,
            'block_number' => $blockNumber,
            'block_hash' => $blockHash,
            'confirmed_at' => $bc['confirmed_at'] ?? null
        ],
        'transfer' => $tr ? [
            'status' => $tr['status'],
            'created_at' => $tr['created_at'],
            'updated_at' => $tr['updated_at'],
            'to_user_id' => (int)$tr['to_user_id'],
            // keep existing keys unchanged (even though name may be retailer/distributor)
            'to_user_name' => $tr['to_user_name'],
            'to_user_phone' => $tr['to_user_phone']
        ] : null,

        // ✅ ADDITIVE: full history (optional for journey UI)
        'transfer_history' => $transferHistory,

        'location_update' => $loc ? [
            'location_text' => $loc['location_text'],
            'temperature_c' => $loc['temperature_c'] !== null ? (float)$loc['temperature_c'] : null,
            'remarks' => $loc['remarks'],
            'recorded_at' => $loc['recorded_at']
        ] : null
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load batch details'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
