<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
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

    $st = $pdo->prepare("SELECT id FROM batch_transfers
                         WHERE batch_id = ? AND to_user_id = ?
                           AND status NOT IN ('REJECTED','CANCELLED')
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$batchId, (int)$auth['uid']]);
    $transfer = $st->fetch();
    if (!$transfer) {
        json_out(['ok' => false, 'error' => 'Batch not assigned to distributor'], 403);
    }

    $items = [];

    $st = $pdo->prepare("SELECT event_type, result, actor_role, meta_json, created_at
                         FROM batch_scan_events
                         WHERE batch_id = ?
                         ORDER BY created_at DESC");
    $st->execute([$batchId]);
    while ($r = $st->fetch()) {
        $metaRaw = $r['meta_json'] ? json_decode($r['meta_json'], true) : [];
        $meta = [
            'location_text' => $metaRaw['location_text'] ?? null,
            'temperature_c' => $metaRaw['temperature_c'] ?? null,
            'remarks' => $metaRaw['remarks'] ?? null
        ];
        $items[] = [
            'source' => 'scan',
            'time' => $r['created_at'],
            'event_type' => $r['event_type'],
            'result' => $r['result'],
            'actor_role' => $r['actor_role'],
            'meta' => $meta
        ];
    }

    $st = $pdo->prepare("SELECT event_name, status, tx_hash, block_number, chain_id, created_at, confirmed_at
                         FROM blockchain_events
                         WHERE batch_id = ?
                         ORDER BY COALESCE(confirmed_at, created_at) DESC");
    $st->execute([$batchId]);
    while ($r = $st->fetch()) {
        $items[] = [
            'source' => 'chain',
            'time' => $r['confirmed_at'] ?: $r['created_at'],
            'event_type' => $r['event_name'],
            'status' => $r['status'],
            'chain_id' => $r['chain_id'],
            'tx_hash' => $r['tx_hash'],
            'block_number' => $r['block_number'],
            'confirmed_at' => $r['confirmed_at']
        ];
    }

    usort($items, function ($a, $b) {
        $ta = isset($a['time']) ? strtotime($a['time']) : 0;
        $tb = isset($b['time']) ? strtotime($b['time']) : 0;
        return $tb <=> $ta;
    });

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load history'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
