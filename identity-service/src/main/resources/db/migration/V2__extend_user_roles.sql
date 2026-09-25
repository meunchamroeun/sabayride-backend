-- identity-service/src/main/resources/db/migration/V2__extend_user_roles.sql
--
-- The contract's Me.roles enum is [CUSTOMER, SHOP_OWNER, SHOP_STAFF, ADMIN].
-- V1 only allowed CUSTOMER / ADMIN in user_role (the design keeps shop roles in
-- rental.shop_member). For a single-service demo/test account whose login can
-- surface every app role to a reviewer, we widen the identity CHECK so an
-- account can also carry SHOP_OWNER / SHOP_STAFF directly.
--
-- This is intentionally additive and reversible; it does not touch existing rows.

SET search_path = identity, public;

ALTER TABLE user_role DROP CONSTRAINT IF EXISTS ck_user_role;
ALTER TABLE user_role ADD CONSTRAINT ck_user_role
    CHECK (role IN ('CUSTOMER','SHOP_OWNER','SHOP_STAFF','ADMIN'));
