<?php
require_once __DIR__ . '/../_bootstrap.php';

$auth = require_auth(['FARMER']);
$pdo = pdo();

try {
    $st = $pdo->prepare(
        "SELECT id, full_name, phone_e164, email, location
         FROM users
         WHERE role = 'DISTRIBUTOR' AND is_active = 1
         ORDER BY full_name ASC, id ASC"
    );
    $st->execute();
    $rows = $st->fetchAll();

    $items = [];
    foreach ($rows as $r) {
        $items[] = [
            'id' => (int)$r['id'],
            'full_name' => $r['full_name'],
            'phone_e164' => $r['phone_e164'],
            'email' => $r['email'],
            'location' => $r['location']
        ];
    }

    json_out(['ok' => true, 'items' => $items]);
} catch (Throwable $e) {
    $resp = ['ok' => false, 'error' => 'Failed to load distributors'];
    if (envv('DEV_MODE', '0') === '1') {
        $resp['details'] = $e->getMessage();
    }
    json_out($resp, 500);
}
