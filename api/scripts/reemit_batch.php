<?php
require_once __DIR__ . '/../config.php';

function envv_local(string $key, string $default = ''): string {
    $val = getenv($key);
    if ($val === false || $val === null || $val === '') {
        if (defined($key)) {
            return (string)constant($key);
        }
        return $default;
    }
    return (string)$val;
}

function now_local(): string {
    return date('Y-m-d H:i:s');
}

function pdo_local(): PDO {
    $dsn = 'mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
    return new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
}

function chain_base_url_local(): string {
    $url = envv_local('CHAIN_BRIDGE_URL', 'http://127.0.0.1:5055');
    return rtrim($url, '/');
}

function chain_post_local(string $path, array $payload, int $timeout = 6): array {
    $url = chain_base_url_local() . $path;
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST => true,
        CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
        CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_SLASHES),
        CURLOPT_TIMEOUT => $timeout,
    ]);

    $body = curl_exec($ch);
    $err = curl_error($ch);
    $status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($body === false || $err) {
        return ['ok' => false, 'error' => 'Bridge connection failed'];
    }

    $data = json_decode($body, true);
    if (!is_array($data)) {
        return ['ok' => false, 'error' => 'Bridge response invalid', 'status' => $status];
    }

    if ($status < 200 || $status >= 300) {
        return ['ok' => false, 'error' => $data['error'] ?? 'Bridge error', 'status' => $status, 'data' => $data];
    }

    return ['ok' => true, 'data' => $data, 'status' => $status];
}

$batchIdArg = isset($argv[1]) ? (int)$argv[1] : 0;

$pdo = pdo_local();

$where = '';
$params = [];
if ($batchIdArg > 0) {
    $where = 'AND b.id = ?';
    $params[] = $batchIdArg;
}

$st = $pdo->prepare("SELECT b.id, b.batch_code, s.hash_hex
                     FROM batches b
                     JOIN batch_hash_snapshots s ON s.batch_id = b.id
                     WHERE s.id = (
                        SELECT s2.id FROM batch_hash_snapshots s2
                        WHERE s2.batch_id = b.id
                        ORDER BY s2.version DESC, s2.id DESC LIMIT 1
                     )
                     " . ($where ? $where : "") . "
                     ORDER BY b.id ASC");
$st->execute($params);
$rows = $st->fetchAll();

if (!$rows) {
    echo "No batches found.\n";
    exit(0);
}

foreach ($rows as $r) {
    $batchId = (int)$r['id'];
    $batchCode = (string)$r['batch_code'];
    $hashHex = (string)$r['hash_hex'];

    try {
        $bridge = chain_post_local('/chain/batchCreated', [
            'batch_code' => $batchCode,
            'hash_hex' => $hashHex
        ]);
        if (!$bridge['ok']) {
            echo "Batch {$batchId} {$batchCode}: bridge error: " . ($bridge['error'] ?? 'unknown') . "\n";
            continue;
        }

        $data = $bridge['data'];
        $pdo->prepare("INSERT INTO blockchain_events (event_name, batch_id, chain_id, tx_hash, block_number, payload_hash_hex, status, confirmed_at)
                       VALUES (?,?,?,?,?,?,?,?)")
            ->execute([
                'BatchCreated',
                $batchId,
                $data['chain_id'] ?? 'local',
                $data['tx_hash'] ?? '',
                $data['block_number'] ?? null,
                $hashHex,
                $data['status'] ?? 'CONFIRMED',
                ($data['status'] ?? '') === 'CONFIRMED' ? now_local() : null
            ]);

        if (($data['status'] ?? '') === 'CONFIRMED') {
            $pdo->prepare("UPDATE batches SET status='ACTIVE' WHERE id=?")->execute([$batchId]);
        }

        echo "Batch {$batchId} {$batchCode}: re-emitted OK.\n";
    } catch (Throwable $e) {
        echo "Batch {$batchId} {$batchCode}: error: " . $e->getMessage() . "\n";
    }
}
