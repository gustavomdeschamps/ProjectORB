# PROJECT ORB — Demo Completa

Demo jogável em Java 21 + LibGDX baseada no GDD e no deck **Project ORB — O mecanismo de combate**.

## Conceito

O combate não abre um quiz. Cada inimigo é uma forma geométrica e pede uma propriedade da própria forma. O jogador responde com a mira, acertando pontos fracos localizados no corpo do inimigo.

- Losango: vértices opostos.
- Triângulo: vértices.
- Quadrado: lados.
- Hexágono: ângulos.
- Núcleo Geométrico (chefe): vértices, lados, simetria e rotação.

Errar não remove vida automaticamente: o erro faz o inimigo atacar. O ataque ainda pode ser evitado usando movimento, salto e dash.

## Fase da demonstração

1. Corredor — movimento, salto, dash e tiro.
2. Câmara — inimigo Losango.
3. Ponte — dois Triângulos.
4. Sala — inimigo Quadrado.
5. Passagem — inimigo Hexágono.
6. Arena — Núcleo Geométrico em três fases.

## Controles

- `A / D` ou setas: mover.
- `ESPAÇO`: salto / duplo salto.
- `SHIFT`: dash.
- `MOUSE`: mirar.
- `BOTÃO ESQUERDO`: atirar.
- `E`: atravessar o Rift depois do chefe.
- `ESC`: pausa.

## Rodar

No terminal, na pasta raiz:

```powershell
.\gradlew :lwjgl3:run
```

É necessário JDK 21.

## Estrutura

- `core/`: gameplay, level, entidades e telas.
- `lwjgl3/`: launcher desktop.
- `assets/`: sprites, fundos, HUD, efeitos e sons.

## Escopo técnico

O GDD completo prevê Box2D, Ashley, gdx-ai e Tiled. Esta demo mantém o foco do deck de cinco minutos e usa colisão AABB determinística para reduzir dependências e tornar o protótipo mais direto. A dependência do Box2D já está incluída no projeto para uma migração futura sem alterar o design do jogo.

## Validação realizada

- Todos os fontes Java foram compilados com JDK 21 contra uma camada de API compatível com os métodos LibGDX usados.
- Lógica dos cinco tipos de inimigo + boss foi testada automaticamente.
- 6 momentos da fase e 39 superfícies de colisão foram validados.
- Referências diretas e animações de assets foram verificadas; nenhum asset utilizado está ausente.

## Revisão visual atual

O jogo usa `assets/processed_remade/` em tempo de execução. O pacote é reproduzível com
`py tools/process_orb_assets.py --remade` e verificável com
`py tools/audit_orb_assets.py --remade`.

- Menu sem chefe decorativo, com ORB apoiado no cenário e entrada "Como jogar".
- Fonte Inconsolata com licença em `assets/fonts/OFL.txt`; botões dimensionam textos longos e têm estado pressionado.
- HUD compacto, mira menor e pontos fracos posicionados sobre os corpos dos inimigos.
- Cenário de gameplay mantém plataformas e portais, sem os props soltos que atrapalhavam a leitura.
- Capturas de QA de menu, instruções, opções, gameplay, inimigo, chefe, derrota e vitória em `tmp/visual-smoke/`.

Consulte `README_IMPLEMENTACAO_ASSETS.md` e `ART_DIRECTION.md` para a origem e os critérios visuais do pacote.
