# Diagrams

Source for the diagrams used across this project.

| Diagram | Where it appears | Source |
|---|---|---|
| System context | [`README.md`](../../README.md) § Architecture | Mermaid, inline |
| Transfer sequence | [`README.md`](../../README.md) § A single transfer | Mermaid, inline |
| Event topology | [`../../backend/EVENT-FLOW.md`](../../backend/EVENT-FLOW.md) | Mermaid, inline |
| ELK pipeline | [`../architecture/observability.md`](../architecture/observability.md) | described |

## Rendering Mermaid locally

Any of these work without an account:

- VS Code — the **Markdown Preview Mermaid Support** extension
- GitHub — render the `.md` in a comment or a gist; GitHub renders Mermaid natively
- <https://mermaid.live> — paste and export SVG/PNG
- `npx @mermaid-js/mermaid-cli -i diagram.mmd -o diagram.svg`

## Regenerating the architecture images for export

To produce PNGs for a CV or a slide deck:

```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i system-context.mmd -o docs/diagrams/system-context.png -b transparent -w 2000
mmdc -i transfer-sequence.mmd -o docs/diagrams/transfer-sequence.png -b transparent -w 2400
```

The `.mmd` sources are the Mermaid blocks lifted verbatim from the README, so a
diagram can never drift from the document that describes it.