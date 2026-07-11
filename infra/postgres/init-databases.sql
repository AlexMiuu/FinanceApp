-- Runs once on first container start (docker-entrypoint-initdb.d).
-- Database-per-service: no service may touch another service's database.
CREATE DATABASE users_db;
CREATE DATABASE expenses_db;
CREATE DATABASE reports_db;
CREATE DATABASE quests_db;
CREATE DATABASE notifications_db;
