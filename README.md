# Criador de Figurinhas

App Android para criar pacotes de figurinhas e adicioná-los ao WhatsApp.

## O que ele faz

- Você escolhe o **nome do pacote** e o **nome do criador** (aparece no WhatsApp como autor).
- Escolhe uma foto da galeria e ela vira figurinha na hora, inteira e sem recorte (centralizada num quadrado com fundo transparente).
- O app converte para o formato do WhatsApp: WebP 512x512, abaixo de 100 KB.
- O ícone do pacote (96x96) é feito automaticamente a partir da primeira figurinha.
- O botão **Adicionar ao WhatsApp** envia o pacote (mínimo 3, máximo 30 figurinhas).
- Funciona com WhatsApp e WhatsApp Business.

## Como gerar o APK (sem PC, pelo GitHub)

1. Crie um repositório vazio no GitHub (ex.: `figurinhas`).
2. No Termux, dentro da pasta do projeto:

```bash
pkg install git
git init
git add .
git commit -m "Primeira versão"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/figurinhas.git
git push -u origin main
```

O GitHub pede usuário e senha; no lugar da senha use um *Personal Access Token*
(GitHub → Settings → Developer settings → Personal access tokens).

3. No GitHub, abra a aba **Actions**, espere o job **Gerar APK** terminar (uns 3 a 6 minutos).
4. Abra a execução concluída, baixe o artefato **figurinhas-apk** (vem em .zip), extraia e instale o `app-debug.apk`.
   Se o Android pedir, permita "instalar apps de fontes desconhecidas".

Também dá para abrir a pasta no Android Studio e usar **Build → Build APK(s)**.

## Estrutura

```
app/src/main/java/com/figurinhas/app/
  MainActivity.kt     tela principal e botão do WhatsApp
  StickerStore.kt     salva o pacote (nome, criador, figurinhas)
  StickerProvider.kt  ContentProvider que o WhatsApp lê
  ImageUtils.kt       converte para WebP 512x512 e cria o ícone 96x96
  StickerAdapter.kt   grade de figurinhas
.github/workflows/build-apk.yml   gera o APK automaticamente
```

## Regras do WhatsApp usadas

- Pacote com 3 a 30 figurinhas.
- Figurinha: WebP 512x512, até 100 KB.
- Ícone do pacote: 96x96, até 50 KB.
- Cada pacote tem nome e criador (publisher).
