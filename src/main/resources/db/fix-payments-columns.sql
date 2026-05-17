-- Run on skinme_db if POS cash / pickup payment fails with:
-- "Data truncated for column 'method' at row 1"
ALTER TABLE payments MODIFY COLUMN method VARCHAR(32) NOT NULL;
ALTER TABLE payments MODIFY COLUMN status VARCHAR(32) NOT NULL;
