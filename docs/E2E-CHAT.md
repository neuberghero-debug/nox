# NOX E2E chat (Modul 21)

Client-to-client encrypted chat over a dumb relay. The relay sees only
fingerprinted ciphertext envelopes.

## Crypto
- **Identity**: X25519 keypair, generated once, stored `nox/chat.key`
  (Base64 PKCS8+X509, machine-wrapped via `NativeCore.wrap`).
- **Fingerprint**: first 12 hex of sha256(pubkey) — what users exchange.
- **Per-message**: ephemeral X25519 → `sha256("nox-e2e-1" + sharedSecret + ephPub)`
  → ChaCha20-Poly1305 (12-byte nonce, AAD = to-fingerprint). Tamper-evident,
  forward-secret per message.
- Envelope JSON: `{v,to,eph,nonce,ct}` — relay can't read or modify.

## Relay
`tools/nox_relay.py` — ~80-line asyncio websocket relay. Peers subscribe by
fingerprint hash; envelopes routed by exact `to` match. No storage, no auth
(default bind 127.0.0.1:8788 — run behind your own tunnel for real use).

## Commands
```
/nox chatkey            print your fingerprint (share it out-of-band)
/nox chatrelay <ws://…> set relay
/nox chat <fp> <msg>    send encrypted message
```

## Trust notes
- Fingerprints must be compared out-of-band (Discord/Signal) — the relay could MITM otherwise.
- Keys are machine-bound at rest (`wrap`) but in-memory while the client runs.
- No deniability guarantees; it's encrypted chat, not Signal.
