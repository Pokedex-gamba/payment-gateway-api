drop database if exists payment_history;

create database payment_history;

use payment_history;

CREATE TABLE transactions (
                              payment_id VARCHAR(128) PRIMARY KEY,
                              user_id VARCHAR(255),
                              amount DECIMAL(10, 2),
                              currency VARCHAR(10),
                              status VARCHAR(50),
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);