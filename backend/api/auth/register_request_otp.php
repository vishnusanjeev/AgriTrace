<?php
require __DIR__ . '/_bootstrap.php';

$in = json_in();

$full_name = trim((string)($in['full_name'] ?? ''));
$email = normalize_email($in['email'] ?? null);
$password = (string)($in['password'] ?? '');
$role = role_norm((string)($in['role'] ?? ''));

if ($full_name === '') out(['ok' => false, 'error' => 'Please enter your full name.'], 400);
if ($email === null) out(['ok' => false, 'error' => 'Please enter your email address.'], 400);
if ($role === '') out(['ok' => false, 'error' => 'Please select a valid role.'], 400);
if (strlen($password) < 6) out(['ok' => false, 'error' => 'Password must be at least 6 characters long.'], 400);

$conn = db();

// prevent duplicate email
$st = $conn->prepare("SELECT id FROM users WHERE email_norm = LOWER(?) LIMIT 1");
$st->bind_param("s", $email);
$st->execute();
$res = $st->get_result();
if ($res && $res->fetch_assoc()) out(['ok' => false, 'error' => 'An account with this email already exists. Please log in or use a different email.'], 409);

// create user as inactive until OTP verified
$hash = password_hash($password, PASSWORD_BCRYPT);

$st = $conn->prepare("INSERT INTO users (full_name, email, phone_e164, password_hash, role, is_active, email_verified_at) VALUES (?, ?, NULL, ?, ?, 0, NULL)");
$st->bind_param("ssss", $full_name, $email, $hash, $role);
if (!$st->execute()) out(['ok' => false, 'error' => 'We could not create your account right now. Please try again.'], 500);

$user_id = (int)$conn->insert_id;

// create OTP
$otp = otp_gen(6);
$code_hash = sha256_hex($otp);
$expires_at = gmdate('Y-m-d H:i:s', time() + 10 * 60);

$purpose = 'VERIFY_EMAIL';
$channel = 'EMAIL';
$sent_to = $email;

$st = $conn->prepare("INSERT INTO auth_otps (user_id, purpose, channel, sent_to, code_hash, expires_at) VALUES (?, ?, ?, ?, ?, ?)");
$st->bind_param("isssss", $user_id, $purpose, $channel, $sent_to, $code_hash, $expires_at);
if (!$st->execute()) out(['ok' => false, 'error' => 'We could not generate a verification code. Please try again.'], 500);

// try email (may fail locally)
$sent = send_email_otp($email, $otp, $purpose);
$dev = envv('DEV_MODE', '0') === '1';

out([
    'ok' => true,
    'error' => null,
    'dev_otp' => $dev ? $otp : null,
    'data' => ['sent' => $sent]
]);

