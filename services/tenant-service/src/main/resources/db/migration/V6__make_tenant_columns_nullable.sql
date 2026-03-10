-- Make columns nullable that Hibernate inserts as null when not provided
ALTER TABLE system.tenants ALTER COLUMN timezone DROP NOT NULL;
ALTER TABLE system.tenants ALTER COLUMN plan DROP NOT NULL;
