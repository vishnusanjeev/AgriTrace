<?php
require_once __DIR__ . '/../_bootstrap.php';
require_once __DIR__ . '/../_chain_client.php';

$auth = require_auth(['FARMER']);
$in = json_in();

$cropName = trim((string)($in['crop_name'] ?? ''));
$category = trim((string)($in['category'] ?? ''));
$quantityKg = $in['quantity_kg'] ?? null;
$harvestDate = trim((string)($in['harvest_date'] ?? ''));
$seedVariety = trim((string)($in['seed_variety'] ?? ''));
$fertilizersUsed = trim((string)($in['fertilizers_used'] ?? ''));
$irrigationMethod = strtoupper(trim((string)($in['irrigation_method'] ?? '')));
$isOrganic = (int)($in['is_organic'] ?? 0);

if ($cropName === '') json_out(['ok' => false, 'error' => 'Crop name required'], 422);
if ($category === '') json_out(['ok' => false, 'error' => 'Category required'], 422);
if ($quantityKg === null || !is_numeric($quantityKg) || (float)$quantityKg <= 0) {
    json_out(['ok' => false, 'error' => 'Quantity must be greater than 0'], 422);
}
if ($harvestDate !== '' && !DateTime::createFromFormat('Y-m-d', $harvestDate)) {
    json_out(['ok' => false, 'error' => 'Invalid harvest_date format'], 422);
}

$allowedIrrigation = ['DRIP','SPRINKLER','FLOOD','RAINFED'];
if ($irrigationMethod !== '' && !in_array($irrigationMethod, $allowedIrrigation, true)) {
    json_out(['ok' => false, 'error' => 'Invalid irrigation_method'], 422);
}

$isOrganic = $isOrganic ? 1 : 0;
$pdo = pdo();

try {
    $pdo->beginTransaction();

    $st = $pdo->prepare("INSERT INTO crops (farmer_user_id, crop_name, category) VALUES (?,?,?)");
    $st->execute([(int)$auth['uid'], $cropName, $category]);
    $cropId = (int)$pdo->lastInsertId();

    $batchCode = 'B-' . date('Ymd-His');
    $st = $pdo->prepare("SELECT id FROM batches WHERE batch_code=? LIMIT 1");
    $st->execute([$batchCode]);
    if ($st->fetch()) {
        $batchCode = $batchCode . '-' . random_int(1000, 9999);
    }

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
        'batch_id' => $batchId,
        'batch_code' => $batchCode,
        'crop_id' => $cropId,
        'farmer_user_id' => (int)$auth['uid'],
        'crop_name' => $cropName,
        'category' => $category,
        'quantity_kg' => (string)$quantityKg,
        'harvest_date' => $harvestDate !== '' ? $harvestDate : null,
        'seed_variety' => $seedVariety !== '' ? $seedVariety : null,
        'fertilizers_used' => $fertilizersUsed !== '' ? $fertilizersUsed : null,
        'irrigation_method' => $irrigationMethod !== '' ? $irrigationMethod : null,
        'is_organic' => $isOrganic,
        'status' => 'PENDING'
    ];

    $canonicalJson = json_encode(canonicalize($canonical), JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    $hashHex = strtolower(hash('sha256', $canonicalJson));

    $st = $pdo->prepare("INSERT INTO batch_hash_snapshots (batch_id, version, hash_algo, hash_hex, canonical_json)
                         VALUES (?,?,?,?,?)");
    $st->execute([$batchId, 1, 'SHA256', $hashHex, $canonicalJson]);

    // Auto-generate QR payload for the farmer at batch creation.
    $nonce = bin2hex(random_bytes(6));
    $qrPayload = '';
    for ($i = 0; $i < 3; $i++) {
        $qrPayload = 'AGRITRACE|v1|batch_id=' . $batchId . '|batch_code=' . $batchCode . '|hash=' . $hashHex . '|nonce=' . $nonce;
        $st = $pdo->prepare("SELECT id FROM qr_codes WHERE qr_payload=? LIMIT 1");
        $st->execute([$qrPayload]);
        if (!$st->fetch()) break;
        $nonce = bin2hex(random_bytes(6));
    }

    $st = $pdo->prepare("INSERT INTO qr_codes (batch_id, generated_by_user_id, qr_payload)
                         VALUES (?,?,?)");
    $st->execute([$batchId, (int)$auth['uid'], $qrPayload]);

    $bridge = chain_post('/chain/batchCreated', [
        'batch_code' => $batchCode,
        'hash_hex' => $hashHex
    ]);

    if ($bridge['ok']) {
        $data = $bridge['data'];
        $st = $pdo->prepare("INSERT INTO blockchain_events (event_name, batch_id, chain_id, tx_hash, block_number, payload_hash_hex, status)
                             VALUES ('BatchCreated',?,?,?,?,?,?)");
        $st->execute([
            $batchId,
            $data['chain_id'] ?? 'hardhat-local',
            $data['tx_hash'] ?? '',
            $data['block_number'] ?? null,
            $hashHex,
            $data['status'] ?? 'SUBMITTED'
        ]);

        $finalStatus = 'PENDING';
        if (($data['status'] ?? '') === 'CONFIRMED') {
            $st = $pdo->prepare("UPDATE batches SET status='ACTIVE' WHERE id=?");
            $st->execute([$batchId]);

            $st = $pdo->prepare("UPDATE blockchain_events SET confirmed_at=NOW() WHERE batch_id=? AND event_name='BatchCreated' AND status='CONFIRMED'");
            $st->execute([$batchId]);

            $finalStatus = 'ACTIVE';
        }

        $pdo->commit();

        json_out([
            'ok' => true,
            'crop' => ['id' => $cropId],
            'batch' => [
                'id' => $batchId,
                'batch_code' => $batchCode,
                'status' => $finalStatus
            ],
            'snapshot' => [
                'version' => 1,
                'hash_hex' => $hashHex
            ],
            'qr' => [
                'batch_id' => $batchId,
                'qr_payload' => $qrPayload
            ],
            'blockchain' => [
                'event_name' => 'BatchCreated',
                'chain_id' => $data['chain_id'] ?? 'hardhat-local',
                'contract_address' => $data['contract_address'] ?? null,
                'tx_hash' => $data['tx_hash'] ?? '',
                'block_number' => $data['block_number'] ?? null,
                'status' => $data['status'] ?? 'SUBMITTED'
            ]
        ]);
    }

    $pdo->prepare("INSERT INTO blockchain_events (event_name, batch_id, chain_id, tx_hash, payload_hash_hex, status)
                   VALUES ('BatchCreated',?,?,?,?, 'FAILED')")
        ->execute([$batchId, 'hardhat-local', '', $hashHex]);

    $pdo->commit();

    json_out([
        'ok' => false,
        'error' => $bridge['error'] ?? 'Blockchain bridge unavailable',
        'crop' => ['id' => $cropId],
        'batch' => [
            'id' => $batchId,
            'batch_code' => $batchCode,
            'status' => 'PENDING'
        ],
        'snapshot' => [
            'version' => 1,
            'hash_hex' => $hashHex
        ],
        'qr' => [
            'batch_id' => $batchId,
            'qr_payload' => $qrPayload
        ],
        'blockchain' => [
            'event_name' => 'BatchCreated',
            'status' => 'FAILED'
        ]
    ], 502);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    $resp = ['ok' => false, 'error' => 'Failed to create crop/batch'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
