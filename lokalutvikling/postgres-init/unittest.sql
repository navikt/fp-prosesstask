CREATE DATABASE unittest;
CREATE USER unittest WITH PASSWORD 'unittest';
GRANT ALL PRIVILEGES ON DATABASE unittest TO unittest;
ALTER DATABASE unittest SET timezone TO 'Europe/Oslo';
ALTER DATABASE unittest OWNER TO unittest;
