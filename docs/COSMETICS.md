# NOX cosmetics workshop

## .cosmetic package (format 1)
Single JSON file — portable cosmetic item:
```json
{
  "format": 1, "name": "Frost Crown", "author": "Player123",
  "slot": "hat|pet|aura|cape|trail",
  "model": "frostcrown",          // glb name for hat/pet/aura
  "model_b64": "…",               // embedded glb (optional)
  "cape_b64": "…",                // embedded png (capes)
  "tint": "#aabbcc", "scale": 1.0, "offY": -8.5, "premium": false
}
```
`cosmetics/CosmeticDef` parses, installs embedded assets into `nox/models|nox/capes` (slugged), applies the matching `Cosmetics` setting.

## Sources
- Built-ins: `assets/noxclient/models/*.glb`, `textures/cape/*.png`
- User drop-in: `nox/models/*.glb`, `nox/capes/*.png` (picked up live)
- Marketplace: `cosmetics/CosmeticsMarket` — server `nox-license-server` /cosmetics/* (JWT-gated download, moderated publish, 5-report auto-flag)

## Client commands
```
/nox cosmetic browse [slot]     list approved items
/nox cosmetic get <id>          download + install + apply
/nox cosmetic export <slot>     write current slot → nox/cosmetics/*.cosmetic
/nox cosmetic import <file>     local install
/nox cosmetic publish <file>    submit for moderation
```

## Rendering
`cosmetics/GlbModel` (minimal glTF2 loader: POSITION/NORMAL/TEXCOORD_0/COLOR_0, node TRS baked at load) → `CosmeticLayer` (entities), `NoxCapeLayer` (PlayerSkin.cape swap — `ClientAsset.ResourceTexture`). Cape cloth sim settings live on the Cosmetics module (`capePhysics/capeWind/capeFlex/capeDistance`).
