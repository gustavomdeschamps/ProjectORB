# Project ORB — Implementação do pacote ORB_2D_ASSETS

## Atualização: pacote redesenhado ativo

O jogo agora carrega `assets/processed_remade/`, gerado das novas folhas em
`assets/source_remade/`. O pacote inclui ORB, quatro inimigos, chefe, cenário,
background, efeitos, marcadores e molduras de interface. Os pacotes
`source_final/` e `processed/` foram preservados como referência/backup.

Para reconstruir e auditar a arte ativa:

```powershell
py tools/process_orb_assets.py --remade
py tools/audit_orb_assets.py --remade
```

O resultado visual atual pode ser visto em `ASSETS_REFEITOS_PREVIEW.png` e nas
capturas `tmp/visual-smoke/`. Consulte `ART_DIRECTION.md` para paleta, pivôs,
transparência e limites da verificação. As seções abaixo documentam a etapa
anterior de integração; onde mencionam `processed/` como pacote ativo, leia-se
`processed_remade/` para esta versão.

Esta versão integra o pacote visual recebido em `ORB_2D_ASSETS/FINAL` ao protótipo jogável do Project ORB.

## O que foi feito

- Spritesheets finais processados programaticamente em PNGs limpos e transparentes.
- Frames separados por animação, com células consistentes e sem labels visíveis.
- Redimensionamento somente com nearest-neighbor.
- Filtro de todas as texturas configurado como `Nearest` e `ClampToEdge`.
- Orb roxa integrada com idle, walk, dash, attack, hurt e death.
- Triângulo vermelho e quadrado azul integrados ao combate educacional existente.
- Diamante amarelo e hexágono verde processados, carregados e disponíveis para expansão; aparecem na apresentação/menu sem alterar a ordem pedagógica da demo.
- Boss geométrico integrado com idle, power-up, orbes, beam, enraged e destruição.
- Tiles, ruínas, plataformas, cristais, pilares, arcos, checkpoint, portal, objetos e background final processados e aplicados ao cenário.
- Câmera com look-ahead suave, alinhamento de posição ao pixel e zoom especial no boss.
- HUD reorganizado para evitar sobreposições.
- Ordem de renderização separada entre background, decoração traseira, plataformas, inimigos, projéteis, player, efeitos, decoração frontal e HUD.
- Animações de morte terminam antes da liberação da passagem/portal.

## Observação sobre Círculo e Pentágono (desatualizado)

O texto original desta seção dizia que Círculo e Pentágono continuariam usando os
sprites da versão anterior. Isso foi revisto: aqueles dois sprites tinham fundo
escuro opaco e destoavam da direção visual nova. As formas do pacote FINAL
cobrem o conteúdo pedagógico da fase, então o elenco passou a ser
**Losango, Triângulo, Quadrado e Hexágono** mais o chefe. Os PNGs antigos
continuam em `assets/enemies/` apenas como referência, sem uso em jogo.

## Estrutura dos novos assets

```text
assets/
├── processed/
│   ├── player/orb/
│   ├── enemies/
│   │   ├── triangle/
│   │   ├── square/
│   │   ├── diamond/
│   │   └── hexagon/
│   ├── boss/
│   ├── world/
│   ├── background/
│   └── ui/
└── source_final/
```

`source_final/` contém as folhas originais e não é alterada pelo processamento. O jogo carrega somente os arquivos de `processed/`.

## Reprodução e auditoria

Com Python 3, Pillow e NumPy instalados, na raiz do projeto:

```powershell
py tools/process_orb_assets.py
py tools/audit_orb_assets.py
```

O processador usa caminhos relativos ao projeto e não apaga as folhas originais. A auditoria verifica formato, dimensões, transparência, quadros vazios, personagem ausente em animações de combate e contato com as bordas do canvas. Ela também gera `assets/processed/audit.json` e `assets/processed/world_contact.png` para inspeção visual.

Na revisão de setembro de 2026, recortes de todas as animações e peças de cenário foram reavaliados. Foram corrigidos textos da folha capturados em frames, fragmentos de sprites vizinhos e o ataque do hexágono que perdia o corpo em um quadro. Os projéteis do chefe são objetos de gameplay; a animação de orbes usa poses de carregamento limpas para não embutir projéteis cortados na silhueta.

## Controles

- `A / D` ou setas: mover
- `ESPAÇO`: pular / duplo salto
- `SHIFT`: dash
- Mouse esquerdo: atirar
- `ESC`: pausar

## Como rodar no Windows

Na raiz do projeto:

```powershell
.\gradlew :lwjgl3:run
```

ou:

```powershell
.\gradlew.bat :lwjgl3:run
```

Requer Java 21.

## Validação desta entrega

- O smoke test LWJGL3 abriu as telas principais e gravou capturas em `tmp/visual-smoke/`: **PASS**.
- 177 quadros de animação e 46 peças do cenário verificados pela auditoria reproduzível: **0 erros**.
- Nenhuma folha `spritesheet.png`, `source_final/` ou `REFERENCIAS` é carregada pelo código do jogo.

### Limitação do ambiente de validação

Nesta máquina, `gradlew test` falhou ao criar a conexão de loopback usada pelo daemon; o smoke test LWJGL3 foi executado com as dependências locais já presentes no cache. Isso valida abertura/renderização das telas, mas não substitui uma rodada manual completa de gameplay e combate.
