<?php
require_once __DIR__ . '/config.php';

date_default_timezone_set('Asia/Kolkata');
header('Content-Type: application/json; charset=utf-8');
if (ob_get_level() === 0) {
    ob_start();
}

function json_out(array $payload, int $code = 200): void {
    http_response_code($code);
    if (ob_get_length()) {
        ob_clean();
    }
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function json_in(): array {
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') return [];
    $data = json_decode($raw, true);
    return is_array($data) ? $data : [];
}

function envv(string $key, string $default = ''): string {
    $val = getenv($key);
    if ($val === false || $val === null || $val === '') {
        if (defined($key)) {
            return (string)constant($key);
        }
        return $default;
    }
    return (string)$val;
}

function now(): string {
    return date('Y-m-d H:i:s');
}

function pdo(): PDO {
    static $pdo = null;
    if ($pdo) return $pdo;

    $dsn = 'mysql:host=' . DB_HOST . ';port=' . DB_PORT . ';dbname=' . DB_NAME . ';charset=utf8mb4';
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    $pdo->exec("SET time_zone = '+05:30'");
    return $pdo;
}

function b64url_decode(string $in): string {
    $s = strtr($in, '-_', '+/');
    $pad = strlen($s) % 4;
    if ($pad > 0) {
        $s .= str_repeat('=', 4 - $pad);
    }
    $out = base64_decode($s, true);
    return $out === false ? '' : $out;
}

function jwt_verify(string $token): ?array {
    $parts = explode('.', $token);
    if (count($parts) !== 3) return null;

    [$h64, $p64, $s64] = $parts;
    $header = json_decode(b64url_decode($h64), true);
    $payload = json_decode(b64url_decode($p64), true);
    if (!is_array($header) || !is_array($payload)) return null;

    if (($header['alg'] ?? '') !== 'HS256') return null;

    $sig = hash_hmac('sha256', $h64 . '.' . $p64, JWT_SECRET, true);
    $sig64 = rtrim(strtr(base64_encode($sig), '+/', '-_'), '=');
    if (!hash_equals($sig64, $s64)) return null;

    if (isset($payload['exp']) && time() > (int)$payload['exp']) return null;
    
    // Verify token_version hasn't changed (for logout-all functionality)
    if (isset($payload['tv'])) {
        $uid = (int)($payload['uid'] ?? 0);
        if ($uid > 0) {
            try {
                $pdo = pdo();
                $st = $pdo->prepare("SELECT token_version FROM users WHERE id = ? LIMIT 1");
                $st->execute([$uid]);
                $user = $st->fetch();
                if ($user && (int)$user['token_version'] !== (int)$payload['tv']) {
                    return null; // Token version mismatch - user logged out elsewhere
                }
            } catch (Throwable $e) {
                // If DB check fails, still allow token (graceful degradation)
            }
        }
    }
    
    return $payload;
}

function get_bearer_token(): ?string {
    $headers = function_exists('getallheaders') ? getallheaders() : [];
    $auth = $headers['Authorization'] ?? $headers['authorization'] ?? $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!is_string($auth) || $auth === '') return null;
    if (stripos($auth, 'Bearer ') !== 0) return null;
    return trim(substr($auth, 7));
}

function require_auth(array $roles = []): array {
    $token = get_bearer_token();
    if (!$token) json_out(['ok' => false, 'error' => 'Unauthorized'], 401);

    $payload = jwt_verify($token);
    if (!$payload) json_out(['ok' => false, 'error' => 'Invalid token'], 401);

    $role = strtoupper((string)($payload['role'] ?? ''));
    $uid = (int)($payload['uid'] ?? 0);
    if ($uid <= 0 || $role === '') json_out(['ok' => false, 'error' => 'Invalid token'], 401);

    if ($roles && !in_array($role, $roles, true)) {
        json_out(['ok' => false, 'error' => 'Forbidden'], 403);
    }

    return ['uid' => $uid, 'role' => $role, 'token' => $token, 'payload' => $payload];
}

function canonicalize($data) {
    if (is_array($data)) {
        $isAssoc = array_keys($data) !== range(0, count($data) - 1);
        if ($isAssoc) {
            ksort($data);
        }
        foreach ($data as $k => $v) {
            $data[$k] = canonicalize($v);
        }
    }
    return $data;
}

function norm_email(string $email): string {
    $email = trim($email);
    $email = mb_strtolower($email, 'UTF-8');
    return $email;
}
