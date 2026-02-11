<?php
require_once __DIR__ . '/_bootstrap.php';

$in = json_in();
$emailRaw = trim((string)($in['email'] ?? ''));
$purpose  = strtoupper(trim((string)($in['purpose'] ?? 'VERIFY_EMAIL')));

if ($emailRaw === '') json_out(['ok'=>false,'error'=>'Email required'], 400);
$email = norm_email($emailRaw);

if (!in_array($purpose, ['VERIFY_EMAIL'], true)) {
    json_out(['ok'=>false,'error'=>'Invalid purpose'], 400);
}

$pdo = pdo();

// Try pending registration first (email not verified yet)
$st = $pdo->prepare("SELECT id FROM pending_registrations WHERE email_norm=? LIMIT 1");
$st->execute([$email]);
$pending = $st->fetch();

$uid = null;
if (!$pending) {
    $st = $pdo->prepare("SELECT id, role, full_name FROM users WHERE email_norm=? LIMIT 1");
    $st->execute([$email]);
    $u = $st->fetch();
    if (!$u) json_out(['ok'=>false,'error'=>'User not found'], 404);
    $uid = (int)$u['id'];
}

// rate limit
if ($pending) {
    $st = $pdo->prepare("SELECT issued_at FROM pending_registrations WHERE email_norm=? LIMIT 1");
    $st->execute([$email]);
    $last = $st->fetch();
} else {
    $st = $pdo->prepare("SELECT issued_at FROM auth_otps
                         WHERE sent_to=? AND purpose=? AND channel='EMAIL'
                         ORDER BY id DESC LIMIT 1");
    $st->execute([$email, $purpose]);
    $last = $st->fetch();
}
if ($last) {
    $lastTs = strtotime($last['issued_at']);
    if ($lastTs && (time() - $lastTs) < OTP_MIN_RESEND_SECONDS) {
        json_out(['ok'=>true,'message'=>'OTP recently sent. Please wait a moment and try again.']);
    }
}

$otp = otp_gen(6);
$hash = sha256_hex($otp);
$expires_at = date('Y-m-d H:i:s', time() + OTP_TTL_SECONDS);

try {
    $pdo->beginTransaction();

    if ($pending) {
        $st = $pdo->prepare("UPDATE pending_registrations
            SET otp_hash=?, otp_expires_at=?, otp_attempts=0, otp_max_attempts=?,
                issued_at=?, request_ip=?, user_agent=?
            WHERE email_norm=?");
        $st->execute([
            $hash, $expires_at, OTP_MAX_ATTEMPTS,
            now(), ($_SERVER['REMOTE_ADDR'] ?? null), ($_SERVER['HTTP_USER_AGENT'] ?? null),
            $email
        ]);
    } else {
        $st = $pdo->prepare("INSERT INTO auth_otps(user_id,purpose,channel,sent_to,code_hash,expires_at,max_attempts,request_ip,user_agent)
                             VALUES(?,?,?,?,?,?,?,?,?)");
        $st->execute([
            $uid, $purpose, 'EMAIL', $email, $hash, $expires_at, OTP_MAX_ATTEMPTS,
            ($_SERVER['REMOTE_ADDR'] ?? null),
            ($_SERVER['HTTP_USER_AGENT'] ?? null)
        ]);

        audit($pdo, $uid, 'AUTH_VERIFY_EMAIL_OTP_REQUEST', ['email'=>$email]);
    }

    $sent = send_email_otp($email, $otp, $purpose);
    if (!$sent) {
        $pdo->rollBack();
        json_out(['ok'=>false,'error'=>'Email service not configured or unavailable.'], 500);
    }

    $pdo->commit();
    $dev = (DEV_MODE === '1');

    json_out([
        'ok' => true,
        'message' => 'OTP sent',
        'data' => ['sent' => $sent],
        'dev_otp' => $dev ? $otp : null
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    json_out(['ok'=>false,'error'=>'Failed to send OTP'], 500);
}
