-- Create the appointments database for the appointment-service
CREATE DATABASE IF NOT EXISTS microcare_appointments;
GRANT ALL PRIVILEGES ON microcare_appointments.* TO 'microcare_user'@'%';
FLUSH PRIVILEGES;
