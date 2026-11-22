-- V007: refresh-token lookup without a user context (the back-end knows only the token when it refreshes a session).
-- Row-level security hides every token from rw_app until a user id is set, so two narrow SECURITY DEFINER functions are provided.

-- Valid, unexpired, unrevoked token -> marks it as used (rotation) and returns its user id; otherwise NULL.
CREATE FUNCTION consume_refresh_token(p_hash text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = rw, public AS
$$
DECLARE v_user uuid;
BEGIN
  UPDATE refresh_tokens SET revoked_at = now()
   WHERE token_hash = p_hash AND revoked_at IS NULL AND expires_at > now()
  RETURNING user_id INTO v_user;
  RETURN v_user;
END
$$;

CREATE FUNCTION revoke_refresh_token(p_hash text) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = rw, public AS
$$ UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = p_hash AND revoked_at IS NULL $$;

GRANT EXECUTE ON FUNCTION consume_refresh_token(text), revoke_refresh_token(text) TO app_write;
