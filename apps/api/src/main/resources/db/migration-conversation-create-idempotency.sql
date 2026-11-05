ALTER TABLE conversations ADD COLUMN client_request_id VARCHAR(128) NULL;
ALTER TABLE conversations ADD UNIQUE KEY uk_conversation_client_request (user_id, client_request_id);
