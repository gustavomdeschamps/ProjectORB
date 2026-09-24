# Project ORB — Rebuild V2

Esta versão corrige o combate e reorganiza completamente a leitura visual da demonstração.

## Principais mudanças

- Pontos fracos agora são grandes, pulsantes e acompanhados por seta e rótulo `ALVO`.
- HUD mostra explicitamente onde atirar e quantos pontos ainda faltam.
- Inimigos normais morrem após resolver uma propriedade completa.
- A hitbox dos pontos fracos foi aumentada para facilitar a demonstração.
- Tiros corretos conseguem atravessar o corpo visual da forma até chegar ao ponto fraco.
- Tiros do jogador não são bloqueados pelas plataformas.
- Círculo, Triângulo, Quadrado, Pentágono e todas as 3 fases do Boss foram validados por teste automático de trajetória.
- Cenário foi simplificado para não esconder inimigos nem colocar assets uns sobre os outros.
- Assets foram refeitos com identidade neon sci-fi mais consistente.
- HUD, plataformas, personagem, inimigos, portal, gate, cristais, efeitos e fundos foram refeitos.

## Controles

- A / D ou setas: mover
- Espaço: salto / salto duplo
- Shift: dash
- Mouse esquerdo: atirar
- ESC: pausa
- E: atravessar o portal final

## Rodar

No terminal, na pasta `ProjectORB`:

```powershell
.\gradlew :lwjgl3:run
```

## Regra do combate

Atire somente nos pontos amarelos/rosa marcados com `ALVO`. Quando todos os pontos pedidos forem atingidos, o inimigo é derrotado ou avança para o próximo padrão.
