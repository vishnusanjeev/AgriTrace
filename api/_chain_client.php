<?php
require_once __DIR__ . '/_bootstrap.php';

function chain_base_url(): string {
    $url = envv('CHAIN_BRIDGE_URL', 'http://127.0.0.1:5055');
    return rtrim($url, '/');
}

function chain_post(string $path, array $payload, int $timeout = 6): array {
    $url = chain_base_url() . $path;
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

function chain_get(string $path, int $timeout = 6): array {
    $url = chain_base_url() . $path;
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
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
