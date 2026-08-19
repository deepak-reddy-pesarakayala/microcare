-- Create the billing database for the billing-service
CREATE DATABASE IF NOT EXISTS microcare_billing;
GRANT ALL PRIVILEGES ON microcare_billing.* TO 'microcare_user'@'%';
FLUSH PRIVILEGES;
