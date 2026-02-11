<?php
require __DIR__ . '/_bootstrap.php';

$in = json_in();
$identifier = trim((string)($in['identifier'] ?? ''));
if ($identifier === '') out(['ok' => false, 'error' => 'Please enter your email address.'], 400);

$conn = db();
$is_email = strpos($identifier, '@') !== false;

if (!$is_email) out(['ok' => false, 'error' => 'Please use your email address to reset your password.'], 400);

$email = normalize_email($identifier);

$st = $conn->prepare("SELECT id, email FROM users WHERE email_norm = LOWER(?) LIMIT 1");
$st->bind_param("s", $email);
$st->execute();
$u = $st->get_result()->fetch_assoc();
if (!$u) out(['ok' => false, 'error' => 'No account found with that email.'], 404);

$user_id = (int)$u['id'];

$otp = otp_gen(6);
$code_hash = sha256_hex($otp);
$expires_at = gmdate('Y-m-d H:i:s', time() + 10 * 60);

$purpose = 'RESET_PASSWORD';
$channel = 'EMAIL';
$sent_to = $email;

$st = $conn->prepare("INSERT INTO auth_otps (user_id, purpose, channel, sent_to, code_hash, expires_at) VALUES (?, ?, ?, ?, ?, ?)");
$st->bind_param("isssss", $user_id, $purpose, $channel, $sent_to, $code_hash, $expires_at);
if (!$st->execute()) out(['ok' => false, 'error' => 'We could not generate a reset code. Please try again.'], 500);

$sent = send_email_otp($email, $otp, $purpose);
$dev = envv('DEV_MODE', '0') === '1';

out(['ok' => true, 'dev_otp' => $dev ? $otp : null, 'data' => ['sent' => $sent]]);

