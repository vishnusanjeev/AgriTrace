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

    $st = $pdo->prepare("SELECT b.batch_code, c.crop_name,
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

    $items = [];
    $pushItem = function ($time, $title, $desc, $tag1 = null, $tag2 = null) use (&$items) {
        $items[] = [
            'time' => $time,
            'title' => $title,
            'description' => $desc,
            'tag1' => $tag1,
            'tag2' => $tag2
        ];
    };

    $st = $pdo->prepare("SELECT event_type, result, actor_role, meta_json, created_at
                         FROM batch_scan_events
                         WHERE batch_id = ?
                         ORDER BY created_at DESC");
    $st->execute([$batchId]);
    while ($r = $st->fetch()) {
        $metaRaw = $r['meta_json'] ? json_decode($r['meta_json'], true) : [];
        $evt = $r['event_type'];
        $time = $r['created_at'];

        if ($evt === 'TRANSFER_ACCEPTED') {
            $pushItem($time, 'Transfer Accepted', 'Assignment accepted by distributor.', null, 'Distributor');
        } elseif ($evt === 'PICKUP_CONFIRMED') {
            $pushItem($time, 'Pickup from Farm', 'Batch picked up for distribution.', null, 'Distributor');
        } elseif ($evt === 'TRANSPORT_UPDATE') {
            $loc = $metaRaw['location_text'] ?? null;
            $temp = $metaRaw['temperature_c'] ?? null;
            $desc = $loc ? "Checkpoint at $loc." : "Transport checkpoint recorded.";
            if ($temp !== null && $temp !== '') {
                $desc .= " Temp: {$temp}C.";
            }
            $pushItem($time, 'In Transit - Checkpoint', $desc, $loc, 'Distributor');
        } elseif ($evt === 'RETAILER_RECEIPT_CONFIRMED') {
            $pushItem($time, 'Arrived at Store', 'Retailer confirmed receipt.', $metaRaw['store_location'] ?? null, 'Retailer');
        } elseif ($evt === 'RETAILER_MARKED_AVAILABLE') {
            $pushItem($time, 'Available at Store', 'Product available for sale.', null, 'Retailer');
        } elseif ($evt === 'RETAILER_MARKED_SOLD') {
            $pushItem($time, 'Sold', 'Product sold to consumer.', null, 'Retailer');
        } elseif ($evt === 'CONSUMER_SCAN') {
            $pushItem($time, 'Consumer Scan', 'Product verified by consumer.', null, 'Consumer');
        }
    }

    $st = $pdo->prepare("SELECT location_text, temperature_c, remarks, recorded_at
                         FROM batch_location_updates
                         WHERE batch_id = ?
                         ORDER BY recorded_at DESC");
    $st->execute([$batchId]);
    while ($r = $st->fetch()) {
        $desc = $r['location_text'] ? "Checkpoint at {$r['location_text']}." : "Checkpoint recorded.";
        if ($r['temperature_c'] !== null) {
            $desc .= " Temp: {$r['temperature_c']}C.";
        }
        if ($r['remarks']) {
            $desc .= " {$r['remarks']}";
        }
        $pushItem($r['recorded_at'], 'In Transit - Checkpoint', $desc, $r['location_text'], 'Distributor');
    }

    $st = $pdo->prepare("SELECT event_name, tx_hash, status, created_at, confirmed_at
                         FROM blockchain_events
                         WHERE batch_id = ?
                         ORDER BY COALESCE(confirmed_at, created_at) DESC");
    $st->execute([$batchId]);
    while ($r = $st->fetch()) {
        $desc = $r['tx_hash'] ? "Tx: {$r['tx_hash']}" : 'Blockchain record';
        $pushItem($r['confirmed_at'] ?: $r['created_at'], 'Blockchain Record', $desc, $r['event_name'], $r['status']);
    }

    usort($items, function ($a, $b) {
        $ta = isset($a['time']) ? strtotime($a['time']) : 0;
        $tb = isset($b['time']) ? strtotime($b['time']) : 0;
        return $tb <=> $ta;
    });

    json_out([
        'ok' => true,
        'batch' => [
            'batch_id' => $batchId,
            'batch_code' => $batch['batch_code'],
            'crop_name' => $batch['crop_name'],
            'farmer_name' => $batch['farmer_name'],
            'farmer_location' => $batch['farmer_location']
        ],
        'items' => $items
    ]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load journey'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
