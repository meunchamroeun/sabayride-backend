-- Allow email addresses (up to 255 chars) in otp_code.phone column
-- so both Phone SMS and Email OTP can be handled in 1 unified table
ALTER TABLE otp_code ALTER COLUMN phone TYPE varchar(255);
