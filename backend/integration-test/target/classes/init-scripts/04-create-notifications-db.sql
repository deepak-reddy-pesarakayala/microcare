-- Create the notifications database for the notification-service
CREATE DATABASE IF NOT EXISTS microcare_notifications;
GRANT ALL PRIVILEGES ON microcare_notifications.* TO 'microcare_user'@'%';
FLUSH PRIVILEGES;
