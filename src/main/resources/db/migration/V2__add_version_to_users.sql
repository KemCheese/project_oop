-- =====================================================================
-- V2__add_version_to_users.sql - BO SUNG KHOA LAC QUAN CHO BANG USERS
-- =====================================================================
-- Module: user  |  Chu so huu: Nguoi C
-- Ngay thuc hien: 2026-09-30
-- Muc tieu: Khac phuc rui ro tuong tranh CONC-02 (Lost Update on User),
-- dam bao Hibernate Optimistic Locking (@Version) hoat dong dong bo
-- giua Admin va Nguoi dung khi cap nhat ho so.
-- =====================================================================

ALTER TABLE users ADD COLUMN version BIGINT;
