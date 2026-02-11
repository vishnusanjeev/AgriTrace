<?php
// api/config.php
define('DB_HOST', getenv('DB_HOST') ?: '127.0.0.1');
define('DB_NAME', getenv('DB_NAME') ?: 'agritrace');
define('DB_USER', getenv('DB_USER') ?: 'root');
define('DB_PASS', getenv('DB_PASS') ?: '');
define('DB_PORT', getenv('DB_PORT') ?: '3306');

// JWT
define('JWT_SECRET', getenv('JWT_SECRET') ?: '9f6c1d4a2b7e3c5d8f0a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6');
define('JWT_TTL', 3600); // 1 hour
define('REFRESH_TTL_DAYS', 30);

// OTP
define('DEV_MODE', getenv('DEV_MODE') ?: '1'); // 1 = include dev_otp in API responses
define('OTP_TTL_SECONDS', 600); // 10 minutes
define('OTP_MAX_ATTEMPTS', 5);
define('OTP_MIN_RESEND_SECONDS', 60); // rate-limit

// SMTP (required for OTP emails in production)
define('SMTP_HOST', 'smtp.gmail.com');
define('SMTP_USER', 'newone3549@gmail.com');
define('SMTP_PASS', 'srgxxzobtrvowere');
define('SMTP_PORT', '587'); // 587 (STARTTLS) or 465 (SSL)
define('SMTP_SECURE', 'tls'); // tls or ssl
define('SMTP_FROM_EMAIL', 'newone3549@gmail.com');
define('SMTP_FROM_NAME', 'AgriTrace');
define('SUPPORT_EMAIL', 'support@agritrace.example');
