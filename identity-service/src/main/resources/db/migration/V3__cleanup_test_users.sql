-- identity-service/src/main/resources/db/migration/V3__cleanup_test_users.sql
-- Deletes specific test users so they can be freshly registered during testing.

SET search_path = identity, public;

DELETE FROM users WHERE phone IN ('+85570483980', '070483980', '+855977869406', '0977869406');
DELETE FROM otp_code WHERE phone IN ('+85570483980', '070483980', '+855977869406', '0977869406');
