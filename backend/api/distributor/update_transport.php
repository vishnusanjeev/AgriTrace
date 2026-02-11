<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['DISTRIBUTOR']);
$pdo = pdo();

$in = json_in();
$batchId = (int)($in['batch_id'] ?? 0);
$eventTime = trim((string)($in['event_time'] ?? ''));
$vehicleId = trim((string)($in['vehicle_id'] ?? ''));
$location = trim((string)($in['location_text'] ?? ''));
$temperature = $in['temperature_c'] ?? null;
$storage = trim((string)($in['storage_conditions'] ?? ''));

if ($batchId <= 0) json_out(['ok' => false, 'error' => 'batch_id required'], 422);
if ($vehicleId === '') json_out(['ok' => false, 'error' => 'vehicle_id required'], 422);
if ($location === '') json_out(['ok' => false, 'error' => 'location_text required'], 422);

try {
    $pdo->beginTransaction();

    $st = $pdo->prepare("SELECT t.id, t.status
                         FROM batch_transfers t
                         JOIN users u ON u.id = t.to_user_id
                         WHERE t.batch_id = ? AND t.from_user_id = ? AND u.role = 'RETAILER'
                         ORDER BY t.id DESC LIMIT 1
                         FOR UPDATE");
    $st->execute([$batchId, (int)$auth['uid']]);
    $retailerTransfer = $st->fetch();
    if (!$retailerTransfer) {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Retailer assignment required'], 403);
    }

    $status = (string)$retailerTransfer['status'];
    if (in_array($status, ['REJECTED', 'CANCELLED'], true)) {
        $pdo->rollBack();
        json_out(['ok' => false, 'error' => 'Transfer not active'], 403);
    }

    $meta = [
        'event_time' => $eventTime !== '' ? $eventTime : null,
        'vehicle_id' => $vehicleId,
        'location_text' => $location,
        'temperature_c' => $temperature,
        'storage_conditions' => $storage !== '' ? $storage : null
    ];

    $st = $pdo->prepare("INSERT INTO batch_location_updates (batch_id, actor_user_id, location_text, temperature_c, remarks)
                         VALUES (?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $location,
        is_null($temperature) ? null : (float)$temperature,
        $storage !== '' ? $storage : null
    ]);

    $st = $pdo->prepare("INSERT INTO batch_scan_events (batch_id, actor_user_id, actor_role, event_type, result, meta_json)
                         VALUES (?,?,?,?,?,?)");
    $st->execute([
        $batchId,
        (int)$auth['uid'],
        $auth['role'],
        'TRANSPORT_UPDATE',
        'OK',
        json_encode($meta, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE)
    ]);

    $pdo->commit();
    json_out(['ok' => true]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    $resp = ['ok' => false, 'error' => 'Failed to update transport'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
