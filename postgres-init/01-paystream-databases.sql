-- PayStream microservices each own a dedicated Postgres database
-- (database-per-service), all served by the single dev postgres container.
CREATE DATABASE paystream_accounts;
CREATE DATABASE paystream_neft;
CREATE DATABASE paystream_rtgs;
CREATE DATABASE paystream_imps;
CREATE DATABASE paystream_upi;
CREATE DATABASE paystream_transactions;
CREATE DATABASE paystream_fraud;
CREATE DATABASE paystream_audit;
