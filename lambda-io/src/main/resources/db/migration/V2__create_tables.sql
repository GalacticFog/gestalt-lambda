-- ----------------------------------------------------------------------------
-- LAMBDA
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS lambda CASCADE;
CREATE TABLE lambda(
  id TEXT NOT NULL,
  is_public boolean NOT NULL,
  artifact_description TEXT NOT NULL,
  payload TEXT,

  CONSTRAINT pk_lambda PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- RESULTS
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS result CASCADE;
CREATE TABLE result(
  execution_id TEXT NOT NULL,
  lambda_id TEXT NOT NULL,
  execution_time timestamp with time zone NOT NULL,
  content_type TEXT NOT NULL,
  result TEXT NOT NULL,
  log TEXT,

  CONSTRAINT fk_lambda_id FOREIGN KEY (lambda_id)
    REFERENCES lambda (id) MATCH SIMPLE
  ON DELETE CASCADE,

  CONSTRAINT pk_result PRIMARY KEY (execution_id)
);

