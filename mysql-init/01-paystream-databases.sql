-- PayStream microservices each own a dedicated MySQL database
-- (database-per-service), all served by the single dev mysql container.
CREATE DATABASE IF NOT EXISTS paystream_auth;
CREATE DATABASE IF NOT EXISTS paystream_customers;
CREATE DATABASE IF NOT EXISTS paystream_notifications;
