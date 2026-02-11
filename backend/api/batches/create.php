<?php
require_once __DIR__ . '/../_bootstrap.php';
require_once __DIR__ . '/../_chain_client.php';

$auth = require_auth(['FARMER']);
$in = json_in();

$cropId = (int)($in['crop_id'] ?? 0);
$batchCodeIn = trim((string)($in['batch_code'] ?? ''));
$quantityKg = $in['quantity_kg'] ?? null;
$harvestDate = trim((string)($in['harvest_date'] ?? ''));
$seedVariety = trim((string)($in['seed_variety'] ?? ''));
$fertilizersUsed = trim((string)($in['fertilizers_used'] ?? ''));
$irrigationMethod = strtoupper(trim((string)($in['irrigation_method'] ?? '')));
$isOrganic = (int)($in['is_organic'] ?? 0);

if ($cropId <= 0) json_out(['ok' => false, 'error' => 'Crop ID required'], 400);
if ($quantityKg === null || !is_numeric($quantityKg)) json_out(['ok' => false, 'error' => 'Quantity required'], 400);
if ($harvestDate !== '' && !DateTime::createFromFormat('Y-m-d', $harvestDate)) {
    json_out(['ok' => false, 'error' => 'Invalid harvest_date format'], 400);
}

$allowedIrrigation = ['DRIP','SPRINKLER','FLOOD','RAINFED'];
if ($irrigationMethod !== '' && !in_array($irrigationMethod, $allowedIrrigation, true)) {
    json_out(['ok' => false, 'error' => 'Invalid irrigation_method'], 400);
}

$isOrganic = $isOrganic ? 1 : 0;

$pdo = pdo();

try {
    $st = $pdo->prepare("SELECT id, crop_name, category, farmer_user_id FROM crops WHERE id=? LIMIT 1");
    $st->execute([$cropId]);
    $crop = $st->fetch();
    if (!$crop) json_out(['ok' => false, 'error' => 'Crop not found'], 404);
    if ((int)$crop['farmer_user_id'] !== (int)$auth['uid']) {
        json_out(['ok' => false, 'error' => 'Forbidden'], 403);
    }

    $batchCode = $batchCodeIn;
    if ($batchCode === '') {
        $batchCode = 'B-' . date('Ymd-His') . '-' . random_int(1000, 9999);
    }

    $st = $pdo->prepare("SELECT * FROM batches WHERE batch_code=? LIMIT 1");
    $st->execute([$batchCode]);
    $existing = $st->fetch();
    if ($existing) {
        if ((int)$existing['created_by_user_id'] !== (int)$auth['uid']) {
            json_out(['ok' => false, 'error' => 'Batch code already used'], 409);
        }
        $ev = $pdo->prepare("SELECT * FROM blockchain_events WHERE batch_id=? ORDER BY id DESC LIMIT 1");
        $ev->execute([(int)$existing['id']]);
        $event = $ev->fetch();

        json_out([
            'ok' => true,
            'crop_id' => (int)$existing['crop_id'],
            'batch' => [
                'id' => (int)$existing['id'],
                'batch_code' => $existing['batch_code'],
                'status' => $existing['status'],
                'quantity_kg' => $existing['quantity_kg'],
                'harvest_date' => $existing['harvest_date'],
                'seed_variety' => $existing['seed_variety'],
                'fertilizers_used' => $existing['fertilizers_used'],
                'irrigation_method' => $existing['irrigation_method'],
                'is_organic' => (int)$existing['is_organic']
            ],
            'blockchain' => $event ? [
                'event' => $event['event_name'],
                'chain_id' => $event['chain_id'],
                'tx_hash' => $event['tx_hash'],
                'block_number' => $event['block_number'],
                'status' => $event['status']
            ] : null
        ]);
    }

    $pdo->beginTransaction();

    $st = $pdo->prepare("INSERT INTO batches (crop_id, created_by_user_id, batch_code, quantity_kg, harvest_date, seed_variety, fertilizers_used, irrigation_method, is_organic, status)
                         VALUES (?,?,?,?,?,?,?,?,?, 'PENDING')");
    $st->execute([
        $cropId,
        (int)$auth['uid'],
        $batchCode,
        $quantityKg,
        $harvestDate !== '' ? $harvestDate : null,
        $seedVariety !== '' ? $seedVariety : null,
        $fertilizersUsed !== '' ? $fertilizersUsed : null,
        $irrigationMethod !== '' ? $irrigationMethod : null,
        $isOrganic
    ]);
    $batchId = (int)$pdo->lastInsertId();

    $canonical = [
        'batch_code' => $batchCode,
        'crop' => [
            'id' => (int)$crop['id'],
            'name' => (string)$crop['crop_name'],
            'category' => (string)$crop['category']
        ],
        'quantity_kg' => (string)$quantityKg,
        'harvest_date' => $harvestDate !== '' ? $harvestDate : null,
        'seed_variety' => $seedVariety !== '' ? $seedVariety : null,
        'fertilizers_used' => $fertilizersUsed !== '' ? $fertilizersUsed : null,
        'irrigation_method' => $irrigationMethod !== '' ? $irrigationMethod : null,
        'is_organic' => $isOrganic,
        'farmer_user_id' => (int)$auth['uid']
    ];

    $canonicalJson = json_encode(canonicalize($canonical), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    $hashHex = strtolower(hash('sha256', $canonicalJson));

    $st = $pdo->prepare("INSERT INTO batch_hash_snapshots (batch_id, version, hash_algo, hash_hex, canonical_json)
                         VALUES (?,?,?,?,?)");
    $st->execute([$batchId, 1, 'SHA256', $hashHex, $canonicalJson]);

    $bridge = chain_post('/chain/batchCreated', [
        'batch_code' => $batchCode,
        'hash_hex' => $hashHex
    ]);

    if ($bridge['ok']) {
        $data = $bridge['data'];
        $st = $pdo->prepare("INSERT INTO blockchain_events (event_name, batch_id, chain_id, tx_hash, block_number, payload_hash_hex, status, confirmed_at)
                             VALUES ('BatchCreated',?,?,?,?,?,?,?,?)");
        $st->execute([
            $batchId,
            $data['chain_id'] ?? 'local',
            $data['tx_hash'] ?? '',
            $data['block_number'] ?? null,
            $hashHex,
            $data['status'] ?? 'SUBMITTED',
            ($data['status'] ?? '') === 'CONFIRMED' ? now() : null
        ]);

        if (($data['status'] ?? '') === 'CONFIRMED') {
            $pdo->prepare("UPDATE batches SET status='ACTIVE' WHERE id=?")->execute([$batchId]);
        }
    } else {
        $pdo->prepare("INSERT INTO blockchain_events (event_name, batch_id, chain_id, tx_hash, payload_hash_hex, status)
                       VALUES ('BatchCreated',?,?,?,?, 'FAILED')")
            ->execute([$batchId, 'local', '', $hashHex]);
    }

    $pdo->commit();

    if (!$bridge['ok']) {
        json_out([
            'ok' => false,
            'error' => $bridge['error'] ?? 'Blockchain bridge unavailable',
            'crop_id' => $cropId,
            'batch' => [
                'id' => $batchId,
                'batch_code' => $batchCode,
                'status' => 'PENDING'
            ],
            'blockchain' => [
                'event' => 'BatchCreated',
                'status' => 'FAILED'
            ]
        ], 502);
    }

    $data = $bridge['data'];
    json_out([
        'ok' => true,
        'crop_id' => $cropId,
        'batch' => [
            'id' => $batchId,
            'batch_code' => $batchCode,
            'status' => (($data['status'] ?? '') === 'CONFIRMED') ? 'ACTIVE' : 'PENDING',
            'quantity_kg' => $quantityKg,
            'harvest_date' => $harvestDate !== '' ? $harvestDate : null,
            'seed_variety' => $seedVariety !== '' ? $seedVariety : null,
            'fertilizers_used' => $fertilizersUsed !== '' ? $fertilizersUsed : null,
            'irrigation_method' => $irrigationMethod !== '' ? $irrigationMethod : null,
            'is_organic' => $isOrganic
        ],
        'blockchain' => [
            'event' => 'BatchCreated',
            'chain_id' => $data['chain_id'] ?? 'local',
            'tx_hash' => $data['tx_hash'] ?? '',
            'block_number' => $data['block_number'] ?? null,
            'status' => $data['status'] ?? 'SUBMITTED'
        ]
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    json_out(['ok' => false, 'error' => 'Failed to create batch'], 500);
}
